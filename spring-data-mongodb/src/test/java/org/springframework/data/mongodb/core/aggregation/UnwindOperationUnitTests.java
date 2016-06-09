/*
 * Copyright 2016 the original author or authors.
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
import static org.springframework.data.mongodb.test.util.IsBsonObject.*;

import org.bson.Document;
import org.junit.Test;
import org.springframework.data.mongodb.core.DBObjectTestUtils;

/**
 * Unit tests for {@link UnwindOperation}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 */
public class UnwindOperationUnitTests {

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindWithPathOnlyShouldUsePreMongo32Syntax() {

		UnwindOperation unwindOperation = Aggregation.unwind("a");

		Document pipeline = unwindOperation.toDocument(Aggregation.DEFAULT_CONTEXT);

		assertThat(pipeline, isBsonObject().containing("$unwind", "$a"));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindWithArrayIndexShouldUseMongo32Syntax() {

		UnwindOperation unwindOperation = Aggregation.unwind("a", "index");

		Document unwindClause = extractDbObjectFromUnwindOperation(unwindOperation);

		assertThat(unwindClause,
				isBsonObject().containing("path", "$a").//
						containing("preserveNullAndEmptyArrays", false).//
						containing("includeArrayIndex", "index"));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindWithArrayIndexShouldExposeArrayIndex() {

		UnwindOperation unwindOperation = Aggregation.unwind("a", "index");

		assertThat(unwindOperation.getFields().getField("index"), is(not(nullValue())));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void plainUnwindShouldNotExposeIndex() {

		UnwindOperation unwindOperation = Aggregation.unwind("a");

		assertThat(unwindOperation.getFields().exposesNoFields(), is(true));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void unwindWithPreserveNullShouldUseMongo32Syntax() {

		UnwindOperation unwindOperation = Aggregation.unwind("a", true);

		Document unwindClause = extractDbObjectFromUnwindOperation(unwindOperation);

		assertThat(unwindClause,
				isBsonObject().containing("path", "$a").//
						containing("preserveNullAndEmptyArrays", true).//
						notContaining("includeArrayIndex"));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void lookupBuilderBuildsCorrectClause() {

		UnwindOperation unwindOperation = UnwindOperation.newUnwind().path("$foo").noArrayIndex().skipNullAndEmptyArrays();
		Document pipeline = unwindOperation.toDocument(Aggregation.DEFAULT_CONTEXT);

		assertThat(pipeline, isBsonObject().containing("$unwind", "$foo"));
	}

	/**
	 * @see DATAMONGO-1391
	 */
	@Test
	public void lookupBuilderBuildsCorrectClauseForMongo32() {

		UnwindOperation unwindOperation = UnwindOperation.newUnwind().path("$foo").arrayIndex("myindex")
				.preserveNullAndEmptyArrays();

		Document unwindClause = extractDbObjectFromUnwindOperation(unwindOperation);

		assertThat(unwindClause,
				isBsonObject().containing("path", "$foo").//
						containing("preserveNullAndEmptyArrays", true).//
						containing("includeArrayIndex", "myindex"));
	}

	private Document extractDbObjectFromUnwindOperation(UnwindOperation unwindOperation) {

		Document dbObject = unwindOperation.toDocument(Aggregation.DEFAULT_CONTEXT);
		Document unwindClause = DBObjectTestUtils.getAsDocument(dbObject, "$unwind");
		return unwindClause;
	}
}
