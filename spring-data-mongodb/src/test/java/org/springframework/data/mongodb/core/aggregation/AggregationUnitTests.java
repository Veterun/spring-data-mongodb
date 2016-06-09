/*
 * Copyright 2013-2016 the original author or authors.
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
package org.springframework.data.mongodb.core.aggregation;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.DBObjectTestUtils.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.test.util.IsBsonObject.*;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.data.domain.Sort.Direction;

/**
 * Unit tests for {@link Aggregation}.
 * 
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author Christoph Strobl
 * @author Mark Paluch
 */
public class AggregationUnitTests {

	public @Rule ExpectedException exception = ExpectedException.none();

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullAggregationOperation() {
		newAggregation((AggregationOperation[]) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullTypedAggregationOperation() {
		newAggregation(String.class, (AggregationOperation[]) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNoAggregationOperation() {
		newAggregation(new AggregationOperation[0]);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNoTypedAggregationOperation() {
		newAggregation(String.class, new AggregationOperation[0]);
	}

	/**
	 * @see DATAMONGO-753
	 */
	@Test
	public void checkForCorrectFieldScopeTransfer() {

		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("Invalid reference");
		exception.expectMessage("'b'");

		newAggregation( //
				project("a", "b"), //
				group("a").count().as("cnt"), // a was introduced to the context by the project operation
				project("cnt", "b") // b was removed from the context by the group operation
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT); // -> triggers IllegalArgumentException
	}

	/**
	 * @see DATAMONGO-753
	 */
	@Test
	public void unwindOperationShouldNotChangeAvailableFields() {

		newAggregation( //
				project("a", "b"), //
				unwind("a"), //
				project("a", "b") // b should still be available
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindOperationWithIndexShouldPreserveFields() {

		newAggregation( //
				project("a", "b"), //
				unwind("a", "x"), //
				project("a", "b") // b should still be available
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindOperationWithIndexShouldAddIndexField() {

		newAggregation( //
				project("a", "b"), //
				unwind("a", "x"), //
				project("a", "x") // b should still be available
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void fullUnwindOperationShouldBuildCorrectClause() {

		DBObject agg = newAggregation( //
				unwind("a", "x", true)).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		@SuppressWarnings("unchecked")
		DBObject unwind = ((List<DBObject>) agg.get("pipeline")).get(0);
		assertThat((DBObject) unwind.get("$unwind"),
				isBsonObject(). //
						containing("includeArrayIndex", "x").//
						containing("preserveNullAndEmptyArrays", true));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindOperationWithPreserveNullShouldBuildCorrectClause() {

		DBObject agg = newAggregation( //
				unwind("a", true)).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		@SuppressWarnings("unchecked")
		DBObject unwind = ((List<DBObject>) agg.get("pipeline")).get(0);
		assertThat(unwind,
				isBsonObject().notContaining("includeArrayIndex").containing("preserveNullAndEmptyArrays", true));
	}

	/**
	 * @see DATAMONGO-753
	 */
	@Test
	public void matchOperationShouldNotChangeAvailableFields() {

		newAggregation( //
				project("a", "b"), //
				match(where("a").gte(1)), //
				project("a", "b") // b should still be available
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);
	}

	/**
	 * @see DATAMONGO-788
	 */
	@Test
	public void referencesToGroupIdsShouldBeRenderedAsReferences() {

		Document agg = newAggregation( //
				project("a"), //
				group("a").count().as("aCnt"), //
				project("aCnt", "a") //
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		@SuppressWarnings("unchecked")
		Document secondProjection = ((List<Document>) agg.get("pipeline")).get(2);
		Document fields = getAsDocument(secondProjection, "$project");
		assertThat(fields.get("aCnt"), is((Object) 1));
		assertThat(fields.get("a"), is((Object) "$_id.a"));
	}

	/**
	 * @see DATAMONGO-791
	 */
	@Test
	public void allowAggregationOperationsToBePassedAsIterable() {

		List<AggregationOperation> ops = new ArrayList<AggregationOperation>();
		ops.add(project("a"));
		ops.add(group("a").count().as("aCnt"));
		ops.add(project("aCnt", "a"));

		Document agg = newAggregation(ops).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		@SuppressWarnings("unchecked")
		Document secondProjection = ((List<Document>) agg.get("pipeline")).get(2);
		Document fields = getAsDocument(secondProjection, "$project");
		assertThat(fields.get("aCnt"), is((Object) 1));
		assertThat(fields.get("a"), is((Object) "$_id.a"));
	}

	/**
	 * @see DATAMONGO-791
	 */
	@Test
	public void allowTypedAggregationOperationsToBePassedAsIterable() {

		List<AggregationOperation> ops = new ArrayList<AggregationOperation>();
		ops.add(project("a"));
		ops.add(group("a").count().as("aCnt"));
		ops.add(project("aCnt", "a"));

		Document agg = newAggregation(Document.class, ops).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		@SuppressWarnings("unchecked")
		Document secondProjection = ((List<Document>) agg.get("pipeline")).get(2);
		Document fields = getAsDocument(secondProjection, "$project");
		assertThat(fields.get("aCnt"), is((Object) 1));
		assertThat(fields.get("a"), is((Object) "$_id.a"));
	}

	/**
	 * @see DATAMONGO-838
	 */
	@Test
	public void expressionBasedFieldsShouldBeReferencableInFollowingOperations() {

		Document agg = newAggregation( //
				project("a").andExpression("b+c").as("foo"), //
				group("a").sum("foo").as("foosum") //
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		@SuppressWarnings("unchecked")
		Document secondProjection = ((List<Document>) agg.get("pipeline")).get(1);
		Document fields = getAsDocument(secondProjection, "$group");
		assertThat(fields.get("foosum"), is((Object) new Document("$sum", "$foo")));
	}

	/**
	 * @see DATAMONGO-908
	 */
	@Test
	public void shouldSupportReferingToNestedPropertiesInGroupOperation() {

		Document agg = newAggregation( //
				project("cmsParameterId", "rules"), //
				unwind("rules"), //
				group("cmsParameterId", "rules.ruleType").count().as("totol") //
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		assertThat(agg, is(notNullValue()));

		Document group = ((List<Document>) agg.get("pipeline")).get(2);
		Document fields = getAsDocument(group, "$group");
		Document id = getAsDocument(fields, "_id");

		assertThat(id.get("ruleType"), is((Object) "$rules.ruleType"));
	}

	/**
	 * @see DATAMONGO-924
	 */
	@Test
	public void referencingProjectionAliasesFromPreviousStepShouldReferToTheSameFieldTarget() {

		Document agg = newAggregation( //
				project().and("foo.bar").as("ba") //
				, project().and("ba").as("b") //
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		Document projection0 = extractPipelineElement(agg, 0, "$project");
		assertThat(projection0, is((Document) new Document("ba", "$foo.bar")));

		Document projection1 = extractPipelineElement(agg, 1, "$project");
		assertThat(projection1, is((Document) new Document("b", "$ba")));
	}

	/**
	 * @see DATAMONGO-960
	 */
	@Test
	public void shouldRenderAggregationWithDefaultOptionsCorrectly() {

		Document agg = newAggregation( //
				project().and("a").as("aa") //
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		assertThat(agg,
				is(Document.parse("{ \"aggregate\" : \"foo\" , \"pipeline\" : [ { \"$project\" : { \"aa\" : \"$a\"}}]}")));
	}

	/**
	 * @see DATAMONGO-960
	 */
	@Test
	public void shouldRenderAggregationWithCustomOptionsCorrectly() {

		AggregationOptions aggregationOptions = newAggregationOptions().explain(true).cursor(new Document("foo", 1))
				.allowDiskUse(true).build();

		Document agg = newAggregation( //
				project().and("a").as("aa") //
		) //
				.withOptions(aggregationOptions) //
				.toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		assertThat(agg,
				is(Document.parse("{ \"aggregate\" : \"foo\" , " //
						+ "\"pipeline\" : [ { \"$project\" : { \"aa\" : \"$a\"}}] , " //
						+ "\"allowDiskUse\" : true , " //
						+ "\"explain\" : true , " //
						+ "\"cursor\" : { \"foo\" : 1}}") //
				));
	}

	/**
	 * @see DATAMONGO-954
	 */
	@Test
	public void shouldSupportReferencingSystemVariables() {

		Document agg = newAggregation( //
				project("someKey") //
						.and("a").as("a1") //
						.and(Aggregation.CURRENT + ".a").as("a2") //
				, sort(Direction.DESC, "a") //
				, group("someKey").first(Aggregation.ROOT).as("doc") //
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		Document projection0 = extractPipelineElement(agg, 0, "$project");
		assertThat(projection0, is((Document) new Document("someKey", 1).append("a1", "$a").append("a2", "$$CURRENT.a")));

		Document sort = extractPipelineElement(agg, 1, "$sort");
		assertThat(sort, is((Document) new Document("a", -1)));

		Document group = extractPipelineElement(agg, 2, "$group");
		assertThat(group, is((Document) new Document("_id", "$someKey").append("doc", new Document("$first", "$$ROOT"))));
	}

	/**
	 * @see DATAMONGO-1254
	 */
	@Test
	public void shouldExposeAliasedFieldnameForProjectionsIncludingOperationsDownThePipeline() {

		Document agg = Aggregation.newAggregation(//
				project("date") //
						.and("tags").minus(10).as("tags_count")//
				, group("date")//
						.sum("tags_count").as("count")//
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		Document group = extractPipelineElement(agg, 1, "$group");
		assertThat(getAsDocument(group, "count"), is(new Document().append("$sum", "$tags_count")));
	}

	/**
	 * @see DATAMONGO-1254
	 */
	@Test
	public void shouldUseAliasedFieldnameForProjectionsIncludingOperationsDownThePipelineWhenUsingSpEL() {

		Document agg = Aggregation.newAggregation(//
				project("date") //
						.andExpression("tags-10")//
				, group("date")//
						.sum("tags_count").as("count")//
		).toDbObject("foo", Aggregation.DEFAULT_CONTEXT);

		Document group = extractPipelineElement(agg, 1, "$group");
		assertThat(getAsDocument(group, "count"), is(new Document().append("$sum", "$tags_count")));
	}

	private Document extractPipelineElement(Document agg, int index, String operation) {

		List<Document> pipeline = (List<Document>) agg.get("pipeline");
		return (Document) pipeline.get(index).get(operation);
	}
}
