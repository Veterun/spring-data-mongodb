/*
 * Copyright 2013-2015 the original author or authors.
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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.aggregation.AggregationFunctionExpressions.*;
import static org.springframework.data.mongodb.core.aggregation.Fields.*;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.junit.Test;
import org.springframework.data.mongodb.core.DBObjectTestUtils;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation.ProjectionOperationBuilder;

/**
 * Unit tests for {@link ProjectionOperation}.
 * 
 * @author Oliver Gierke
 * @author Thomas Darimont
 */
public class ProjectionOperationUnitTests {

	static final String MOD = "$mod";
	static final String ADD = "$add";
	static final String SUBTRACT = "$subtract";
	static final String MULTIPLY = "$multiply";
	static final String DIVIDE = "$divide";
	static final String PROJECT = "$project";

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullFields() {
		new ProjectionOperation(null);
	}

	@Test
	public void declaresBackReferenceCorrectly() {

		ProjectionOperation operation = new ProjectionOperation();
		operation = operation.and("prop").previousOperation();

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		assertThat(projectClause.get("prop"), is((Object) Fields.UNDERSCORE_ID_REF));
	}

	@Test
	public void alwaysUsesExplicitReference() {

		ProjectionOperation operation = new ProjectionOperation(Fields.fields("foo").and("bar", "foobar"));

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);

		assertThat(projectClause.get("foo"), is((Object) 1));
		assertThat(projectClause.get("bar"), is((Object) "$foobar"));
	}

	@Test
	public void aliasesSimpleFieldProjection() {

		ProjectionOperation operation = new ProjectionOperation();

		Document dbObject = operation.and("foo").as("bar").toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);

		assertThat(projectClause.get("bar"), is((Object) "$foo"));
	}

	@Test
	public void aliasesArithmeticProjection() {

		ProjectionOperation operation = new ProjectionOperation();

		Document dbObject = operation.and("foo").plus(41).as("bar").toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		Document barClause = DBObjectTestUtils.getAsDocument(projectClause, "bar");
		List<Object> addClause = (List<Object>) barClause.get("$add");

		assertThat(addClause, hasSize(2));
		assertThat(addClause.get(0), is((Object) "$foo"));
		assertThat(addClause.get(1), is((Object) 41));
	}

	public void arithmenticProjectionOperationWithoutAlias() {

		String fieldName = "a";
		ProjectionOperationBuilder operation = new ProjectionOperation().and(fieldName).plus(1);
		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		Document oper = exctractOperation(fieldName, projectClause);

		assertThat(oper.containsKey(ADD), is(true));
		assertThat(oper.get(ADD), is((Object) Arrays.<Object> asList("$a", 1)));
	}

	@Test
	public void arithmenticProjectionOperationPlus() {

		String fieldName = "a";
		String fieldAlias = "b";
		ProjectionOperation operation = new ProjectionOperation().and(fieldName).plus(1).as(fieldAlias);
		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);

		Document oper = exctractOperation(fieldAlias, projectClause);
		assertThat(oper.containsKey(ADD), is(true));
		assertThat(oper.get(ADD), is((Object) Arrays.<Object> asList("$a", 1)));
	}

	@Test
	public void arithmenticProjectionOperationMinus() {

		String fieldName = "a";
		String fieldAlias = "b";
		ProjectionOperation operation = new ProjectionOperation().and(fieldName).minus(1).as(fieldAlias);
		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		Document oper = exctractOperation(fieldAlias, projectClause);

		assertThat(oper.containsKey(SUBTRACT), is(true));
		assertThat(oper.get(SUBTRACT), is((Object) Arrays.<Object> asList("$a", 1)));
	}

	@Test
	public void arithmenticProjectionOperationMultiply() {

		String fieldName = "a";
		String fieldAlias = "b";
		ProjectionOperation operation = new ProjectionOperation().and(fieldName).multiply(1).as(fieldAlias);
		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		Document oper = exctractOperation(fieldAlias, projectClause);

		assertThat(oper.containsKey(MULTIPLY), is(true));
		assertThat(oper.get(MULTIPLY), is((Object) Arrays.<Object> asList("$a", 1)));
	}

	@Test
	public void arithmenticProjectionOperationDivide() {

		String fieldName = "a";
		String fieldAlias = "b";
		ProjectionOperation operation = new ProjectionOperation().and(fieldName).divide(1).as(fieldAlias);
		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		Document oper = exctractOperation(fieldAlias, projectClause);

		assertThat(oper.containsKey(DIVIDE), is(true));
		assertThat(oper.get(DIVIDE), is((Object) Arrays.<Object> asList("$a", 1)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void arithmenticProjectionOperationDivideByZeroException() {

		new ProjectionOperation().and("a").divide(0);
	}

	@Test
	public void arithmenticProjectionOperationMod() {

		String fieldName = "a";
		String fieldAlias = "b";
		ProjectionOperation operation = new ProjectionOperation().and(fieldName).mod(3).as(fieldAlias);
		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		Document oper = exctractOperation(fieldAlias, projectClause);

		assertThat(oper.containsKey(MOD), is(true));
		assertThat(oper.get(MOD), is((Object) Arrays.<Object> asList("$a", 3)));
	}

	/**
	 * @see DATAMONGO-758
	 */
	@Test(expected = IllegalArgumentException.class)
	public void excludeShouldThrowExceptionForFieldsOtherThanUnderscoreId() {

		new ProjectionOperation().andExclude("foo");
	}

	/**
	 * @see DATAMONGO-758
	 */
	@Test
	public void excludeShouldAllowExclusionOfUnderscoreId() {

		ProjectionOperation projectionOp = new ProjectionOperation().andExclude(Fields.UNDERSCORE_ID);
		Document dbObject = projectionOp.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);
		assertThat((Integer) projectClause.get(Fields.UNDERSCORE_ID), is(0));
	}

	/**
	 * @see DATAMONGO-757
	 */
	@Test
	public void usesImplictAndExplicitFieldAliasAndIncludeExclude() {

		ProjectionOperation operation = Aggregation.project("foo").and("foobar").as("bar").andInclude("inc1", "inc2")
				.andExclude("_id");

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);

		assertThat(projectClause.get("foo"), is((Object) 1)); // implicit
		assertThat(projectClause.get("bar"), is((Object) "$foobar")); // explicit
		assertThat(projectClause.get("inc1"), is((Object) 1)); // include shortcut
		assertThat(projectClause.get("inc2"), is((Object) 1));
		assertThat(projectClause.get("_id"), is((Object) 0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void arithmenticProjectionOperationModByZeroException() {

		new ProjectionOperation().and("a").mod(0);
	}

	/**
	 * @see DATAMONGO-769
	 */
	@Test
	public void allowArithmeticOperationsWithFieldReferences() {

		ProjectionOperation operation = Aggregation.project() //
				.and("foo").plus("bar").as("fooPlusBar") //
				.and("foo").minus("bar").as("fooMinusBar") //
				.and("foo").multiply("bar").as("fooMultiplyBar") //
				.and("foo").divide("bar").as("fooDivideBar") //
				.and("foo").mod("bar").as("fooModBar");

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document projectClause = DBObjectTestUtils.getAsDocument(dbObject, PROJECT);

		assertThat((Document) projectClause.get("fooPlusBar"), //
				is(new Document("$add", Arrays.asList("$foo", "$bar"))));
		assertThat((Document) projectClause.get("fooMinusBar"), //
				is(new Document("$subtract", Arrays.asList("$foo", "$bar"))));
		assertThat((Document) projectClause.get("fooMultiplyBar"), //
				is(new Document("$multiply", Arrays.asList("$foo", "$bar"))));
		assertThat((Document) projectClause.get("fooDivideBar"), //
				is(new Document("$divide", Arrays.asList("$foo", "$bar"))));
		assertThat((Document) projectClause.get("fooModBar"), //
				is(new Document("$mod", Arrays.asList("$foo", "$bar"))));
	}

	/**
	 * @see DATAMONGO-774
	 */
	@Test
	public void projectionExpressions() {

		ProjectionOperation operation = Aggregation.project() //
				.andExpression("(netPrice + surCharge) * taxrate * [0]", 2).as("grossSalesPrice") //
				.and("foo").as("bar"); //

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		assertThat(dbObject, is(Document.parse(
				"{ \"$project\" : { \"grossSalesPrice\" : { \"$multiply\" : [ { \"$add\" : [ \"$netPrice\" , \"$surCharge\"]} , \"$taxrate\" , 2]} , \"bar\" : \"$foo\"}}")));
	}

	/**
	 * @see DATAMONGO-975
	 */
	@Test
	public void shouldRenderDateTimeFragmentExtractionsForSimpleFieldProjectionsCorrectly() {

		ProjectionOperation operation = Aggregation.project() //
				.and("date").extractHour().as("hour") //
				.and("date").extractMinute().as("min") //
				.and("date").extractSecond().as("second") //
				.and("date").extractMillisecond().as("millis") //
				.and("date").extractYear().as("year") //
				.and("date").extractMonth().as("month") //
				.and("date").extractWeek().as("week") //
				.and("date").extractDayOfYear().as("dayOfYear") //
				.and("date").extractDayOfMonth().as("dayOfMonth") //
				.and("date").extractDayOfWeek().as("dayOfWeek") //
		;

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		assertThat(dbObject, is(notNullValue()));

		Document projected = exctractOperation("$project", dbObject);

		assertThat(projected.get("hour"), is((Object) new Document("$hour", Arrays.asList("$date"))));
		assertThat(projected.get("min"), is((Object) new Document("$minute", Arrays.asList("$date"))));
		assertThat(projected.get("second"), is((Object) new Document("$second", Arrays.asList("$date"))));
		assertThat(projected.get("millis"), is((Object) new Document("$millisecond", Arrays.asList("$date"))));
		assertThat(projected.get("year"), is((Object) new Document("$year", Arrays.asList("$date"))));
		assertThat(projected.get("month"), is((Object) new Document("$month", Arrays.asList("$date"))));
		assertThat(projected.get("week"), is((Object) new Document("$week", Arrays.asList("$date"))));
		assertThat(projected.get("dayOfYear"), is((Object) new Document("$dayOfYear", Arrays.asList("$date"))));
		assertThat(projected.get("dayOfMonth"), is((Object) new Document("$dayOfMonth", Arrays.asList("$date"))));
		assertThat(projected.get("dayOfWeek"), is((Object) new Document("$dayOfWeek", Arrays.asList("$date"))));
	}

	/**
	 * @see DATAMONGO-975
	 */
	@Test
	public void shouldRenderDateTimeFragmentExtractionsForExpressionProjectionsCorrectly() throws Exception {

		ProjectionOperation operation = Aggregation.project() //
				.andExpression("date + 86400000") //
				.extractDayOfYear() //
				.as("dayOfYearPlus1Day") //
		;

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);
		assertThat(dbObject, is(notNullValue()));

		Document projected = exctractOperation("$project", dbObject);
		assertThat(projected.get("dayOfYearPlus1Day"), is((Object) new Document("$dayOfYear",
				Arrays.asList(new Document("$add", Arrays.<Object> asList("$date", 86400000))))));
	}

	/**
	 * @see DATAMONGO-979
	 */
	@Test
	public void shouldRenderSizeExpressionInProjection() {

		ProjectionOperation operation = Aggregation //
				.project() //
				.and("tags") //
				.size()//
				.as("tags_count");

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);

		Document projected = exctractOperation("$project", dbObject);
		assertThat(projected.get("tags_count"), is((Object) new Document("$size", Arrays.asList("$tags"))));
	}

	/**
	 * @see DATAMONGO-979
	 */
	@Test
	public void shouldRenderGenericSizeExpressionInProjection() {

		ProjectionOperation operation = Aggregation //
				.project() //
				.and(SIZE.of(field("tags"))) //
				.as("tags_count");

		Document dbObject = operation.toDocument(Aggregation.DEFAULT_CONTEXT);

		Document projected = exctractOperation("$project", dbObject);
		assertThat(projected.get("tags_count"), is((Object) new Document("$size", Arrays.asList("$tags"))));
	}

	private static Document exctractOperation(String field, Document fromProjectClause) {
		return (Document) fromProjectClause.get(field);
	}
}
