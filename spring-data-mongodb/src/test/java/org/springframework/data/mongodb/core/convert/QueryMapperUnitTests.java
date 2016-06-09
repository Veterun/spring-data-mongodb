/*
 * Copyright 2011-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core.convert;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.DBObjectTestUtils.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;
import static org.springframework.data.mongodb.test.util.IsBsonObject.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.DBObjectTestUtils;
import org.springframework.data.mongodb.core.Person;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.TextScore;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.QueryBuilder;

/**
 * Unit tests for {@link QueryMapper}.
 * 
 * @author Oliver Gierke
 * @author Patryk Wasik
 * @author Thomas Darimont
 * @author Christoph Strobl
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryMapperUnitTests {

	QueryMapper mapper;
	MongoMappingContext context;
	MappingMongoConverter converter;

	@Mock MongoDbFactory factory;

	@Before
	public void setUp() {

		this.context = new MongoMappingContext();

		this.converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), context);
		this.converter.afterPropertiesSet();

		this.mapper = new QueryMapper(converter);
	}

	@Test
	public void translatesIdPropertyIntoIdKey() {

		org.bson.Document query = new org.bson.Document("foo", "value");
		MongoPersistentEntity<?> entity = context.getPersistentEntity(Sample.class);

		org.bson.Document result = mapper.getMappedObject(query, entity);
		assertThat(result.get("_id"), is(notNullValue()));
		assertThat(result.get("foo"), is(nullValue()));
	}

	@Test
	public void convertsStringIntoObjectId() {

		org.bson.Document query = new org.bson.Document("_id", new ObjectId().toString());
		org.bson.Document result = mapper.getMappedObject(query, context.getPersistentEntity(IdWrapper.class));
		assertThat(result.get("_id"), is(instanceOf(ObjectId.class)));
	}

	@Test
	public void handlesBigIntegerIdsCorrectly() {

		org.bson.Document dbObject = new org.bson.Document("id", new BigInteger("1"));
		org.bson.Document result = mapper.getMappedObject(dbObject, context.getPersistentEntity(IdWrapper.class));
		assertThat(result.get("_id"), is((Object) "1"));
	}

	@Test
	public void handlesObjectIdCapableBigIntegerIdsCorrectly() {

		ObjectId id = new ObjectId();
		org.bson.Document dbObject = new org.bson.Document("id", new BigInteger(id.toString(), 16));
		org.bson.Document result = mapper.getMappedObject(dbObject, context.getPersistentEntity(IdWrapper.class));
		assertThat(result.get("_id"), is((Object) id));
	}

	/**
	 * @see DATAMONGO-278
	 */
	@Test
	public void translates$NeCorrectly() {

		Criteria criteria = where("foo").ne(new ObjectId().toString());

		org.bson.Document result = mapper.getMappedObject(criteria.getCriteriaObject(),
				context.getPersistentEntity(Sample.class));
		Object object = result.get("_id");
		assertThat(object, is(instanceOf(org.bson.Document.class)));
		org.bson.Document dbObject = (org.bson.Document) object;
		assertThat(dbObject.get("$ne"), is(instanceOf(ObjectId.class)));
	}

	/**
	 * @see DATAMONGO-326
	 */
	@Test
	public void handlesEnumsCorrectly() {
		Query query = query(where("foo").is(Enum.INSTANCE));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(), null);

		Object object = result.get("foo");
		assertThat(object, is(instanceOf(String.class)));
	}

	@Test
	public void handlesEnumsInNotEqualCorrectly() {
		Query query = query(where("foo").ne(Enum.INSTANCE));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(), null);

		Object object = result.get("foo");
		assertThat(object, is(instanceOf(org.bson.Document.class)));

		Object ne = ((org.bson.Document) object).get("$ne");
		assertThat(ne, is(instanceOf(String.class)));
		assertThat(ne.toString(), is(Enum.INSTANCE.name()));
	}

	@Test
	public void handlesEnumsIn$InCorrectly() {

		Query query = query(where("foo").in(Enum.INSTANCE));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(), null);

		Object object = result.get("foo");
		assertThat(object, is(instanceOf(org.bson.Document.class)));

		Object in = ((org.bson.Document) object).get("$in");
		assertThat(in, is(instanceOf(List.class)));

		List list = (List) in;
		assertThat(list.size(), is(1));
		assertThat(list.get(0), is(instanceOf(String.class)));
		assertThat(list.get(0).toString(), is(Enum.INSTANCE.name()));
	}

	/**
	 * @see DATAMONGO-373
	 */
	@Test
	public void handlesNativelyBuiltQueryCorrectly() {

		DBObject query = new QueryBuilder().or(new BasicDBObject("foo", "bar")).get();
		mapper.getMappedObject(new org.bson.Document(query.toMap()), null);
	}

	/**
	 * @see DATAMONGO-369
	 */
	@Test
	public void handlesAllPropertiesIfDocument() {

		org.bson.Document query = new org.bson.Document();
		query.put("foo", new org.bson.Document("$in", Arrays.asList(1, 2)));
		query.put("bar", new Person());

		org.bson.Document result = mapper.getMappedObject(query, null);
		assertThat(result.get("bar"), is(notNullValue()));
	}

	/**
	 * @see DATAMONGO-429
	 */
	@Test
	public void transformsArraysCorrectly() {

		Query query = new BasicQuery("{ 'tags' : { '$all' : [ 'green', 'orange']}}");

		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(), null);
		assertThat(result.toJson(), is(query.getQueryObject().toJson()));
	}

	@Test
	public void doesHandleNestedFieldsWithDefaultIdNames() {

		org.bson.Document dbObject = new org.bson.Document("id", new ObjectId().toString());
		dbObject.put("nested", new org.bson.Document("id", new ObjectId().toString()));

		MongoPersistentEntity<?> entity = context.getPersistentEntity(ClassWithDefaultId.class);

		org.bson.Document result = mapper.getMappedObject(dbObject, entity);
		assertThat(result.get("_id"), is(instanceOf(ObjectId.class)));
		assertThat(((org.bson.Document) result.get("nested")).get("_id"), is(instanceOf(ObjectId.class)));
	}

	/**
	 * @see DATAMONGO-493
	 */
	@Test
	public void doesNotTranslateNonIdPropertiesFor$NeCriteria() {

		ObjectId accidentallyAnObjectId = new ObjectId();

		Query query = Query
				.query(Criteria.where("id").is("id_value").and("publishers").ne(accidentallyAnObjectId.toString()));

		org.bson.Document dbObject = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(UserEntity.class));
		assertThat(dbObject.get("publishers"), is(instanceOf(org.bson.Document.class)));

		org.bson.Document publishers = (org.bson.Document) dbObject.get("publishers");
		assertThat(publishers.containsKey("$ne"), is(true));
		assertThat(publishers.get("$ne"), is(instanceOf(String.class)));
	}

	/**
	 * @see DATAMONGO-494
	 */
	@Test
	public void usesEntityMetadataInOr() {

		Query query = query(new Criteria().orOperator(where("foo").is("bar")));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(Sample.class));

		assertThat(result.keySet(), hasSize(1));
		assertThat(result.keySet(), hasItem("$or"));

		List<Object> ors = getAsDBList(result, "$or");
		assertThat(ors, hasSize(1));
		org.bson.Document criterias = getAsDocument(ors, 0);
		assertThat(criterias.keySet(), hasSize(1));
		assertThat(criterias.get("_id"), is(notNullValue()));
		assertThat(criterias.get("foo"), is(nullValue()));
	}

	@Test
	public void translatesPropertyReferenceCorrectly() {

		Query query = query(where("field").is(new CustomizedField()));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(CustomizedField.class));

		assertThat(result.containsKey("foo"), is(true));
		assertThat(result.keySet().size(), is(1));
	}

	@Test
	public void translatesNestedPropertyReferenceCorrectly() {

		Query query = query(where("field.field").is(new CustomizedField()));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(CustomizedField.class));

		assertThat(result.containsKey("foo.foo"), is(true));
		assertThat(result.keySet().size(), is(1));
	}

	@Test
	public void returnsOriginalKeyIfNoPropertyReference() {

		Query query = query(where("bar").is(new CustomizedField()));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(CustomizedField.class));

		assertThat(result.containsKey("bar"), is(true));
		assertThat(result.keySet().size(), is(1));
	}

	@Test
	public void convertsAssociationCorrectly() {

		Reference reference = new Reference();
		reference.id = 5L;

		Query query = query(where("reference").is(reference));
		org.bson.Document object = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(WithDBRef.class));

		Object referenceObject = object.get("reference");

		assertThat(referenceObject, is(instanceOf(com.mongodb.DBRef.class)));
	}

	@Test
	public void convertsNestedAssociationCorrectly() {

		Reference reference = new Reference();
		reference.id = 5L;

		Query query = query(where("withDbRef.reference").is(reference));
		org.bson.Document object = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(WithDBRefWrapper.class));

		Object referenceObject = object.get("withDbRef.reference");

		assertThat(referenceObject, is(instanceOf(com.mongodb.DBRef.class)));
	}

	@Test
	public void convertsInKeywordCorrectly() {

		Reference first = new Reference();
		first.id = 5L;

		Reference second = new Reference();
		second.id = 6L;

		Query query = query(where("reference").in(first, second));
		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(WithDBRef.class));

		org.bson.Document reference = DBObjectTestUtils.getAsDocument(result, "reference");

		List<Object> inClause = getAsDBList(reference, "$in");
		assertThat(inClause, hasSize(2));
		assertThat(inClause.get(0), is(instanceOf(com.mongodb.DBRef.class)));
		assertThat(inClause.get(1), is(instanceOf(com.mongodb.DBRef.class)));
	}

	/**
	 * @see DATAMONGO-570
	 */
	@Test
	public void correctlyConvertsNullReference() {

		Query query = query(where("reference").is(null));
		org.bson.Document object = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(WithDBRef.class));

		assertThat(object.get("reference"), is(nullValue()));
	}

	/**
	 * @see DATAMONGO-629
	 */
	@Test
	public void doesNotMapIdIfNoEntityMetadataAvailable() {

		String id = new ObjectId().toString();
		Query query = query(where("id").is(id));

		org.bson.Document object = mapper.getMappedObject(query.getQueryObject(), null);

		assertThat(object.containsKey("id"), is(true));
		assertThat(object.get("id"), is((Object) id));
		assertThat(object.containsKey("_id"), is(false));
	}

	/**
	 * @see DATAMONGO-677
	 */
	@Test
	public void handleMapWithDBRefCorrectly() {

		org.bson.Document mapDbObject = new org.bson.Document();
		mapDbObject.put("test", new com.mongodb.DBRef("test", "test"));
		org.bson.Document dbObject = new org.bson.Document();
		dbObject.put("mapWithDBRef", mapDbObject);

		org.bson.Document mapped = mapper.getMappedObject(dbObject, context.getPersistentEntity(WithMapDBRef.class));

		assertThat(mapped.containsKey("mapWithDBRef"), is(true));
		assertThat(mapped.get("mapWithDBRef"), instanceOf(org.bson.Document.class));
		assertThat(((org.bson.Document) mapped.get("mapWithDBRef")).containsKey("test"), is(true));
		assertThat(((org.bson.Document) mapped.get("mapWithDBRef")).get("test"), instanceOf(com.mongodb.DBRef.class));
	}

	@Test
	public void convertsUnderscoreIdValueWithoutMetadata() {

		org.bson.Document dbObject = new org.bson.Document().append("_id", new ObjectId().toString());

		org.bson.Document mapped = mapper.getMappedObject(dbObject, null);
		assertThat(mapped.containsKey("_id"), is(true));
		assertThat(mapped.get("_id"), is(instanceOf(ObjectId.class)));
	}

	/**
	 * @see DATAMONGO-705
	 */
	@Test
	public void convertsDBRefWithExistsQuery() {

		Query query = query(where("reference").exists(false));

		BasicMongoPersistentEntity<?> entity = context.getPersistentEntity(WithDBRef.class);
		org.bson.Document mappedObject = mapper.getMappedObject(query.getQueryObject(), entity);

		org.bson.Document reference = getAsDocument(mappedObject, "reference");
		assertThat(reference.containsKey("$exists"), is(true));
		assertThat(reference.get("$exists"), is((Object) false));
	}

	/**
	 * @see DATAMONGO-706
	 */
	@Test
	public void convertsNestedDBRefsCorrectly() {

		Reference reference = new Reference();
		reference.id = 5L;

		Query query = query(where("someString").is("foo").andOperator(where("reference").in(reference)));

		BasicMongoPersistentEntity<?> entity = context.getPersistentEntity(WithDBRef.class);
		org.bson.Document mappedObject = mapper.getMappedObject(query.getQueryObject(), entity);

		assertThat(mappedObject.get("someString"), is((Object) "foo"));

		List<Object> andClause = getAsDBList(mappedObject, "$and");
		assertThat(andClause, hasSize(1));

		List<Object> inClause = getAsDBList(getAsDocument(getAsDocument(andClause, 0), "reference"), "$in");
		assertThat(inClause, hasSize(1));
		assertThat(inClause.get(0), is(instanceOf(com.mongodb.DBRef.class)));
	}

	/**
	 * @see DATAMONGO-752
	 */
	@Test
	public void mapsSimpleValuesStartingWith$Correctly() {

		Query query = query(where("myvalue").is("$334"));

		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(), null);

		assertThat(result.keySet(), hasSize(1));
		assertThat(result.get("myvalue"), is((Object) "$334"));
	}

	/**
	 * @see DATAMONGO-752
	 */
	@Test
	public void mapsKeywordAsSimpleValuesCorrectly() {

		Query query = query(where("myvalue").is("$center"));

		org.bson.Document result = mapper.getMappedObject(query.getQueryObject(), null);

		assertThat(result.keySet(), hasSize(1));
		assertThat(result.get("myvalue"), is((Object) "$center"));
	}

	/**
	 * @DATAMONGO-805
	 */
	@Test
	public void shouldExcludeDBRefAssociation() {

		Query query = query(where("someString").is("foo"));
		query.fields().exclude("reference");

		BasicMongoPersistentEntity<?> entity = context.getPersistentEntity(WithDBRef.class);
		org.bson.Document queryResult = mapper.getMappedObject(query.getQueryObject(), entity);
		org.bson.Document fieldsResult = mapper.getMappedObject(query.getFieldsObject(), entity);

		assertThat(queryResult.get("someString"), is((Object) "foo"));
		assertThat(fieldsResult.get("reference"), is((Object) 0));
	}

	/**
	 * @see DATAMONGO-686
	 */
	@Test
	public void queryMapperShouldNotChangeStateInGivenQueryObjectWhenIdConstrainedByInList() {

		BasicMongoPersistentEntity<?> persistentEntity = context.getPersistentEntity(Sample.class);
		String idPropertyName = persistentEntity.getIdProperty().getName();
		org.bson.Document queryObject = query(where(idPropertyName).in("42")).getQueryObject();

		Object idValuesBefore = getAsDocument(queryObject, idPropertyName).get("$in");
		mapper.getMappedObject(queryObject, persistentEntity);
		Object idValuesAfter = getAsDocument(queryObject, idPropertyName).get("$in");

		assertThat(idValuesAfter, is(idValuesBefore));
	}

	/**
	 * @see DATAMONGO-821
	 */
	@Test
	public void queryMapperShouldNotTryToMapDBRefListPropertyIfNestedInsideDocumentWithinDocument() {

		org.bson.Document queryObject = query(
				where("referenceList").is(new org.bson.Document("$nested", new org.bson.Document("$keys", 0L))))
						.getQueryObject();

		org.bson.Document mappedObject = mapper.getMappedObject(queryObject,
				context.getPersistentEntity(WithDBRefList.class));
		org.bson.Document referenceObject = getAsDocument(mappedObject, "referenceList");
		org.bson.Document nestedObject = getAsDocument(referenceObject, "$nested");

		assertThat(nestedObject, is((org.bson.Document) new org.bson.Document("$keys", 0L)));
	}

	/**
	 * @see DATAMONGO-821
	 */
	@Test
	public void queryMapperShouldNotTryToMapDBRefPropertyIfNestedInsideDocumentWithinDocument() {

		org.bson.Document queryObject = query(
				where("reference").is(new org.bson.Document("$nested", new org.bson.Document("$keys", 0L)))).getQueryObject();

		org.bson.Document mappedObject = mapper.getMappedObject(queryObject, context.getPersistentEntity(WithDBRef.class));
		org.bson.Document referenceObject = getAsDocument(mappedObject, "reference");
		org.bson.Document nestedObject = getAsDocument(referenceObject, "$nested");

		assertThat(nestedObject, is((org.bson.Document) new org.bson.Document("$keys", 0L)));
	}

	/**
	 * @see DATAMONGO-821
	 */
	@Test
	public void queryMapperShouldMapDBRefPropertyIfNestedInDocument() {

		Reference sample = new Reference();
		sample.id = 321L;
		org.bson.Document queryObject = query(where("reference").is(new org.bson.Document("$in", Arrays.asList(sample))))
				.getQueryObject();

		org.bson.Document mappedObject = mapper.getMappedObject(queryObject, context.getPersistentEntity(WithDBRef.class));

		org.bson.Document referenceObject = getAsDocument(mappedObject, "reference");
		List<Object> inObject = getAsDBList(referenceObject, "$in");

		assertThat(inObject.get(0), is(instanceOf(com.mongodb.DBRef.class)));
	}

	/**
	 * @see DATAMONGO-773
	 */
	@Test
	public void queryMapperShouldBeAbleToProcessQueriesThatIncludeDbRefFields() {

		BasicMongoPersistentEntity<?> persistentEntity = context.getPersistentEntity(WithDBRef.class);

		Query qry = query(where("someString").is("abc"));
		qry.fields().include("reference");

		org.bson.Document mappedFields = mapper.getMappedObject(qry.getFieldsObject(), persistentEntity);
		assertThat(mappedFields, is(notNullValue()));
	}

	/**
	 * @see DATAMONGO-893
	 */
	@Test
	public void classInformationShouldNotBePresentInDocumentUsedInFinderMethods() {

		EmbeddedClass embedded = new EmbeddedClass();
		embedded.id = "1";

		EmbeddedClass embedded2 = new EmbeddedClass();
		embedded2.id = "2";
		Query query = query(where("embedded").in(Arrays.asList(embedded, embedded2)));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(), context.getPersistentEntity(Foo.class));
		assertThat(dbo,
				equalTo(org.bson.Document.parse("{ \"embedded\" : { \"$in\" : [ { \"_id\" : \"1\"} , { \"_id\" : \"2\"}]}}")));
	}

	/**
	 * @see DATAMONGO-647
	 */
	@Test
	public void customizedFieldNameShouldBeMappedCorrectlyWhenApplyingSort() {

		Query query = query(where("field").is("bar")).with(new Sort(Direction.DESC, "field"));
		org.bson.Document dbo = mapper.getMappedObject(query.getSortObject(),
				context.getPersistentEntity(CustomizedField.class));
		assertThat(dbo, equalTo(new org.bson.Document().append("foo", -1)));
	}

	/**
	 * @see DATAMONGO-973
	 */
	@Test
	public void getMappedFieldsAppendsTextScoreFieldProperlyCorrectlyWhenNotPresent() {

		Query query = new Query();

		org.bson.Document dbo = mapper.getMappedFields(query.getFieldsObject(),
				context.getPersistentEntity(WithTextScoreProperty.class));

		assertThat(dbo, equalTo(new org.bson.Document().append("score", new org.bson.Document("$meta", "textScore"))));
	}

	/**
	 * @see DATAMONGO-973
	 */
	@Test
	public void getMappedFieldsReplacesTextScoreFieldProperlyCorrectlyWhenPresent() {

		Query query = new Query();
		query.fields().include("textScore");

		org.bson.Document dbo = mapper.getMappedFields(query.getFieldsObject(),
				context.getPersistentEntity(WithTextScoreProperty.class));

		assertThat(dbo, equalTo(new org.bson.Document().append("score", new org.bson.Document("$meta", "textScore"))));
	}

	/**
	 * @see DATAMONGO-973
	 */
	@Test
	public void getMappedSortAppendsTextScoreProperlyWhenSortedByScore() {

		Query query = new Query().with(new Sort("textScore"));

		org.bson.Document dbo = mapper.getMappedSort(query.getSortObject(),
				context.getPersistentEntity(WithTextScoreProperty.class));

		assertThat(dbo, equalTo(new org.bson.Document().append("score", new org.bson.Document("$meta", "textScore"))));
	}

	/**
	 * @see DATAMONGO-973
	 */
	@Test
	public void getMappedSortIgnoresTextScoreWhenNotSortedByScore() {

		Query query = new Query().with(new Sort("id"));

		org.bson.Document dbo = mapper.getMappedSort(query.getSortObject(),
				context.getPersistentEntity(WithTextScoreProperty.class));

		assertThat(dbo, equalTo(new org.bson.Document().append("_id", 1)));
	}

	/**
	 * @see DATAMONGO-1070
	 */
	@Test
	public void mapsIdReferenceToDBRefCorrectly() {

		ObjectId id = new ObjectId();

		org.bson.Document query = new org.bson.Document("reference.id", new com.mongodb.DBRef("reference", id.toString()));
		org.bson.Document result = mapper.getMappedObject(query, context.getPersistentEntity(WithDBRef.class));

		assertThat(result.containsKey("reference"), is(true));
		com.mongodb.DBRef reference = getTypedValue(result, "reference", com.mongodb.DBRef.class);
		assertThat(reference.getId(), is(instanceOf(ObjectId.class)));
	}

	/**
	 * @see DATAMONGO-1050
	 */
	@Test
	public void shouldUseExplicitlySetFieldnameForIdPropertyCandidates() {

		Query query = query(where("nested.id").is("bar"));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(RootForClassWithExplicitlyRenamedIdField.class));

		assertThat(dbo, equalTo(new org.bson.Document().append("nested.id", "bar")));
	}

	/**
	 * @see DATAMONGO-1050
	 */
	@Test
	public void shouldUseExplicitlySetFieldnameForIdPropertyCandidatesUsedInSortClause() {

		Query query = new Query().with(new Sort("nested.id"));

		org.bson.Document dbo = mapper.getMappedSort(query.getSortObject(),
				context.getPersistentEntity(RootForClassWithExplicitlyRenamedIdField.class));

		assertThat(dbo, equalTo(new org.bson.Document().append("nested.id", 1)));
	}

	/**
	 * @see DATAMONGO-1135
	 */
	@Test
	public void nearShouldUseGeoJsonRepresentationOnUnmappedProperty() {

		Query query = query(where("foo").near(new GeoJsonPoint(100, 50)));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(ClassWithGeoTypes.class));

		assertThat(dbo, isBsonObject().containing("foo.$near.$geometry.type", "Point"));
		assertThat(dbo, isBsonObject().containing("foo.$near.$geometry.coordinates.[0]", 100D));
		assertThat(dbo, isBsonObject().containing("foo.$near.$geometry.coordinates.[1]", 50D));
	}

	/**
	 * @see DATAMONGO-1135
	 */
	@Test
	public void nearShouldUseGeoJsonRepresentationWhenMappingToGoJsonType() {

		Query query = query(where("geoJsonPoint").near(new GeoJsonPoint(100, 50)));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(ClassWithGeoTypes.class));

		assertThat(dbo, isBsonObject().containing("geoJsonPoint.$near.$geometry.type", "Point"));
	}

	/**
	 * @see DATAMONGO-1135
	 */
	@Test
	public void nearSphereShouldUseGeoJsonRepresentationWhenMappingToGoJsonType() {

		Query query = query(where("geoJsonPoint").nearSphere(new GeoJsonPoint(100, 50)));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(ClassWithGeoTypes.class));

		assertThat(dbo, isBsonObject().containing("geoJsonPoint.$nearSphere.$geometry.type", "Point"));
	}

	/**
	 * @see DATAMONGO-1135
	 */
	@Test
	public void shouldMapNameCorrectlyForGeoJsonType() {

		Query query = query(where("namedGeoJsonPoint").nearSphere(new GeoJsonPoint(100, 50)));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(ClassWithGeoTypes.class));

		assertThat(dbo,
				isBsonObject().containing("geoJsonPointWithNameViaFieldAnnotation.$nearSphere.$geometry.type", "Point"));
	}

	/**
	 * @see DATAMONGO-1135
	 */
	@Test
	public void withinShouldUseGeoJsonPolygonWhenMappingPolygonOn2DSphereIndex() {

		Query query = query(where("geoJsonPoint")
				.within(new GeoJsonPolygon(new Point(0, 0), new Point(100, 100), new Point(100, 0), new Point(0, 0))));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(ClassWithGeoTypes.class));

		assertThat(dbo, isBsonObject().containing("geoJsonPoint.$geoWithin.$geometry.type", "Polygon"));
	}

	/**
	 * @see DATAMONGO-1134
	 */
	@Test
	public void intersectsShouldUseGeoJsonRepresentationCorrectly() {

		Query query = query(where("geoJsonPoint")
				.intersects(new GeoJsonPolygon(new Point(0, 0), new Point(100, 100), new Point(100, 0), new Point(0, 0))));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(ClassWithGeoTypes.class));

		assertThat(dbo, isBsonObject().containing("geoJsonPoint.$geoIntersects.$geometry.type", "Polygon"));
		assertThat(dbo, isBsonObject().containing("geoJsonPoint.$geoIntersects.$geometry.coordinates"));
	}

	/**
	 * @see DATAMONGO-1269
	 */
	@Test
	public void mappingShouldRetainNumericMapKey() {

		Query query = query(where("map.1.stringProperty").is("ba'alzamon"));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(EntityWithComplexValueTypeMap.class));

		assertThat(dbo.containsKey("map.1.stringProperty"), is(true));
	}

	/**
	 * @see DATAMONGO-1269
	 */
	@Test
	public void mappingShouldRetainNumericPositionInList() {

		Query query = query(where("list.1.stringProperty").is("ba'alzamon"));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(EntityWithComplexValueTypeList.class));

		assertThat(dbo.containsKey("list.1.stringProperty"), is(true));
	}

	/**
	 * @see DATAMONGO-1245
	 */
	@Test
	public void exampleShouldBeMappedCorrectly() {

		Foo probe = new Foo();
		probe.embedded = new EmbeddedClass();
		probe.embedded.id = "conflux";

		Query query = query(byExample(probe));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(), context.getPersistentEntity(Foo.class));

		assertThat(dbo, isBsonObject().containing("embedded\\._id", "conflux"));
	}

	/**
	 * @see DATAMONGO-1245
	 */
	@Test
	public void exampleShouldBeMappedCorrectlyWhenContainingLegacyPoint() {

		ClassWithGeoTypes probe = new ClassWithGeoTypes();
		probe.legacyPoint = new Point(10D, 20D);

		Query query = query(byExample(probe));

		org.bson.Document dbo = mapper.getMappedObject(query.getQueryObject(),
				context.getPersistentEntity(WithDBRef.class));

		assertThat(dbo.get("legacyPoint.x"), Is.<Object> is(10D));
		assertThat(dbo.get("legacyPoint.y"), Is.<Object> is(20D));
	}

	@Document
	public class Foo {
		@Id private ObjectId id;
		EmbeddedClass embedded;
	}

	public class EmbeddedClass {
		public String id;
	}

	class IdWrapper {
		Object id;
	}

	class ClassWithEmbedded {
		@Id String id;
		Sample sample;
	}

	class ClassWithDefaultId {

		String id;
		ClassWithDefaultId nested;
	}

	class Sample {

		@Id private String foo;
	}

	class BigIntegerId {

		@Id private BigInteger id;
	}

	enum Enum {
		INSTANCE;
	}

	class UserEntity {
		String id;
		List<String> publishers = new ArrayList<String>();
	}

	class CustomizedField {

		@Field("foo") CustomizedField field;
	}

	class WithDBRef {

		String someString;
		@DBRef Reference reference;
	}

	class WithDBRefList {

		String someString;
		@DBRef List<Reference> referenceList;
	}

	class Reference {

		Long id;
	}

	class WithDBRefWrapper {

		WithDBRef withDbRef;
	}

	class WithMapDBRef {

		@DBRef Map<String, Sample> mapWithDBRef;
	}

	class WithTextScoreProperty {

		@Id String id;
		@TextScore @Field("score") Float textScore;
	}

	static class RootForClassWithExplicitlyRenamedIdField {

		@Id String id;
		ClassWithExplicitlyRenamedField nested;
	}

	static class ClassWithExplicitlyRenamedField {

		@Field("id") String id;
	}

	static class ClassWithGeoTypes {

		double[] justAnArray;
		Point legacyPoint;
		GeoJsonPoint geoJsonPoint;
		@Field("geoJsonPointWithNameViaFieldAnnotation") GeoJsonPoint namedGeoJsonPoint;
	}

	static class SimpeEntityWithoutId {

		String stringProperty;
		Integer integerProperty;
	}

	static class EntityWithComplexValueTypeMap {
		Map<Integer, SimpeEntityWithoutId> map;
	}

	static class EntityWithComplexValueTypeList {
		List<SimpeEntityWithoutId> list;
	}
}
