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
package org.springframework.data.mongodb.core.convert;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.util.BsonUtils;
import org.springframework.util.Assert;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Wrapper value object for a {@link BasicDocument} to be able to access raw values by {@link MongoPersistentProperty}
 * references. The accessors will transparently resolve nested document values that a {@link MongoPersistentProperty}
 * might refer to through a path expression in field names.
 * 
 * @author Oliver Gierke
 */
class DocumentAccessor {

	private final Bson dbObject;

	/**
	 * Creates a new {@link DocumentAccessor} for the given {@link Document}.
	 * 
	 * @param dbObject must be a {@link BasicDocument} effectively, must not be {@literal null}.
	 */
	public DocumentAccessor(Bson dbObject) {

		Assert.notNull(dbObject, "Document must not be null!");

		if (!(dbObject instanceof Document) && !(dbObject instanceof DBObject)) {
			Assert.isInstanceOf(Document.class, dbObject, "Given Document must be a Document or BasicDBObject!");
		}

		this.dbObject = dbObject;
	}

	/**
	 * Puts the given value into the backing {@link Document} based on the coordinates defined through the given
	 * {@link MongoPersistentProperty}. By default this will be the plain field name. But field names might also consist
	 * of path traversals so we might need to create intermediate {@link BasicDocument}s.
	 * 
	 * @param prop must not be {@literal null}.
	 * @param value
	 */
	public void put(MongoPersistentProperty prop, Object value) {

		Assert.notNull(prop, "MongoPersistentProperty must not be null!");
		String fieldName = prop.getFieldName();

		if (!fieldName.contains(".")) {
			BsonUtils.addToMap(dbObject, fieldName, value);
			return;
		}

		Iterator<String> parts = Arrays.asList(fieldName.split("\\.")).iterator();
		Bson dbObject = this.dbObject;

		while (parts.hasNext()) {

			String part = parts.next();

			if (parts.hasNext()) {
				dbObject = getOrCreateNestedDbObject(part, dbObject);
			} else {
				BsonUtils.addToMap(dbObject, part, value);
			}
		}
	}

	/**
	 * Returns the value the given {@link MongoPersistentProperty} refers to. By default this will be a direct field but
	 * the method will also transparently resolve nested values the {@link MongoPersistentProperty} might refer to through
	 * a path expression in the field name metadata.
	 * 
	 * @param property must not be {@literal null}.
	 * @return
	 */
	public Object get(MongoPersistentProperty property) {

		String fieldName = property.getFieldName();

		if (!fieldName.contains(".")) {
			return BsonUtils.asMap(this.dbObject).get(fieldName);
		}

		Iterator<String> parts = Arrays.asList(fieldName.split("\\.")).iterator();
		Map<String, Object> source = BsonUtils.asMap(this.dbObject);
		Object result = null;

		while (source != null && parts.hasNext()) {

			result = source.get(parts.next());

			if (parts.hasNext()) {
				source = getAsMap(result);
			}
		}

		return result;
	}

	/**
	 * Returns the given source object as map, i.e. {@link BasicDocument}s and maps as is or {@literal null} otherwise.
	 * 
	 * @param source can be {@literal null}.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> getAsMap(Object source) {

		if (source instanceof Document) {
			return (Document) source;
		}

		if (source instanceof BasicDBObject) {
			return (BasicDBObject) source;
		}

		if (source instanceof Map) {
			return (Map<String, Object>) source;
		}

		return null;
	}

	/**
	 * Returns the {@link Document} which either already exists in the given source under the given key, or creates a new
	 * nested one, registers it with the source and returns it.
	 * 
	 * @param key must not be {@literal null} or empty.
	 * @param source must not be {@literal null}.
	 * @return
	 */
	private static Document getOrCreateNestedDbObject(String key, Bson source) {

		Object existing = BsonUtils.asMap(source).get(key);

		if (existing instanceof Document) {
			return (Document) existing;
		}

		Document nested = new Document();
		BsonUtils.addToMap(source, key, nested);

		return nested;
	}
}
