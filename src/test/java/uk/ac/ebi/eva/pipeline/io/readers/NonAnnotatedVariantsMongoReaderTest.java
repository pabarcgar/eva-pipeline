/*
 * Copyright 2016-2017 EMBL - European Bioinformatics Institute
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
package uk.ac.ebi.eva.pipeline.io.readers;

import com.mongodb.DBObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import uk.ac.ebi.eva.commons.models.converters.data.VariantToDBObjectConverter;
import uk.ac.ebi.eva.pipeline.Application;
import uk.ac.ebi.eva.pipeline.configuration.MongoConfiguration;
import uk.ac.ebi.eva.pipeline.parameters.MongoConnection;
import uk.ac.ebi.eva.test.data.VariantData;
import uk.ac.ebi.eva.test.rules.TemporaryMongoRule;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link NonAnnotatedVariantsMongoReader}
 * input: a variants collection address
 * output: a DBObject each time `.read()` is called, with at least: chr, start, annot
 */
@RunWith(SpringRunner.class)
@ActiveProfiles(Application.VARIANT_ANNOTATION_MONGO_PROFILE)
@TestPropertySource({"classpath:test-mongo.properties"})
@ContextConfiguration(classes = {MongoConnection.class, MongoMappingContext.class})
public class NonAnnotatedVariantsMongoReaderTest {

    private static final String COLLECTION_VARIANTS_NAME = "variants";

    private static final int EXPECTED_NON_ANNOTATED_VARIANTS_IN_STUDY = 1;

    private static final int EXPECTED_NON_ANNOTATED_VARIANTS_IN_DB = 2;

    private static final String STUDY_ID = "7";

    private static final String ALL_STUDIES = "";

    @Autowired
    private MongoConnection mongoConnection;

    @Autowired
    private MongoMappingContext mongoMappingContext;

    @Rule
    public TemporaryMongoRule mongoRule = new TemporaryMongoRule();

    @Test
    public void shouldReadVariantsWithoutAnnotationFieldInStudy() throws Exception {
        checkNonAnnotatedVariantsRead(EXPECTED_NON_ANNOTATED_VARIANTS_IN_STUDY, STUDY_ID);
    }

    @Test
    public void shouldReadVariantsWithoutAnnotationField() throws Exception {
        checkNonAnnotatedVariantsRead(EXPECTED_NON_ANNOTATED_VARIANTS_IN_DB, ALL_STUDIES);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStudyIdIsRequired() throws Exception {
        checkNonAnnotatedVariantsRead(EXPECTED_NON_ANNOTATED_VARIANTS_IN_DB, null);
    }

    private void checkNonAnnotatedVariantsRead(int expectedNonAnnotatedVariants, String study) throws Exception {
        ExecutionContext executionContext = MetaDataInstanceFactory.createStepExecution().getExecutionContext();
        String databaseName = mongoRule.createDBAndInsertDocuments(COLLECTION_VARIANTS_NAME, Arrays.asList(
                VariantData.getVariantWithAnnotation(),
                VariantData.getVariantWithoutAnnotation(),
                VariantData.getVariantWithoutAnnotationOtherStudy()));

        MongoOperations mongoOperations = MongoConfiguration.getMongoOperations(databaseName, mongoConnection,
                mongoMappingContext);

        NonAnnotatedVariantsMongoReader mongoItemReader = new NonAnnotatedVariantsMongoReader(
                mongoOperations, COLLECTION_VARIANTS_NAME, study);
        mongoItemReader.open(executionContext);

        int itemCount = 0;
        DBObject variantMongoDocument;
        while ((variantMongoDocument = mongoItemReader.read()) != null) {
            itemCount++;
            assertTrue(variantMongoDocument.containsField(VariantToDBObjectConverter.CHROMOSOME_FIELD));
            assertTrue(variantMongoDocument.containsField(VariantToDBObjectConverter.START_FIELD));
            assertFalse(variantMongoDocument.containsField(VariantToDBObjectConverter.ANNOTATION_FIELD));
        }
        assertEquals(expectedNonAnnotatedVariants, itemCount);
        mongoItemReader.close();
    }
}
