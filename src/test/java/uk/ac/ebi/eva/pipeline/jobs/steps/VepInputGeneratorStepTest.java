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
package uk.ac.ebi.eva.pipeline.jobs.steps;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import uk.ac.ebi.eva.pipeline.Application;
import uk.ac.ebi.eva.pipeline.configuration.BeanNames;
import uk.ac.ebi.eva.pipeline.jobs.AnnotationJob;
import uk.ac.ebi.eva.test.configuration.BatchTestConfiguration;
import uk.ac.ebi.eva.test.rules.PipelineTemporaryFolderRule;
import uk.ac.ebi.eva.test.rules.TemporaryMongoRule;
import uk.ac.ebi.eva.utils.EvaJobParameterBuilder;
import uk.ac.ebi.eva.utils.URLHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.ac.ebi.eva.test.utils.JobTestUtils.getLines;
import static uk.ac.ebi.eva.test.utils.JobTestUtils.readFirstLine;
import static uk.ac.ebi.eva.test.utils.TestFileUtils.getResourceUrl;

/**
 * Test {@link VepInputGeneratorStep}
 */
@RunWith(SpringRunner.class)
@TestPropertySource({"classpath:common-configuration.properties", "classpath:test-mongo.properties"})
@ActiveProfiles(Application.VARIANT_ANNOTATION_MONGO_PROFILE)
@ContextConfiguration(classes = {AnnotationJob.class, BatchTestConfiguration.class})
public class VepInputGeneratorStepTest {

    private static final String MONGO_DUMP = "/dump/VariantStatsConfigurationTest_vl";

    private static final String STUDY_ID = "1";

    private static final String FILE_ID = "1";

    private static final String COLLECTION_VARIANTS_NAME = "variants";

    private static final int EXPECTED_NON_ANNOTATED_VARIANTS = 300;

    private static final String ALL_STUDIES = "";

    @Rule
    public TemporaryMongoRule mongoRule = new TemporaryMongoRule();

    @Rule
    public PipelineTemporaryFolderRule temporaryFolderRule = new PipelineTemporaryFolderRule();

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Test
    public void shouldGenerateVepInputForStudyId() throws Exception {
        assertVepInputWasGenerated(STUDY_ID);
    }

    @Test
    public void shouldAllowEmptyStringAsStudyIdJobParameter() throws Exception {
        assertVepInputWasGenerated(ALL_STUDIES);
    }

    private void assertVepInputWasGenerated(String inputStudyId) throws IOException, InterruptedException {
        String randomTemporaryDatabaseName = mongoRule.restoreDumpInTemporaryDatabase(getResourceUrl(MONGO_DUMP));
        String outputDirAnnot = temporaryFolderRule.getRoot().getAbsolutePath();
        File vepInput = new File(URLHelper.resolveVepInput(outputDirAnnot, inputStudyId, FILE_ID));
        temporaryFolderRule.newFile(vepInput.getName());

        JobParameters jobParameters = new EvaJobParameterBuilder()
                .collectionVariantsName(COLLECTION_VARIANTS_NAME)
                .databaseName(randomTemporaryDatabaseName)
                .inputStudyId(inputStudyId)
                .inputVcfId(FILE_ID)
                .outputDirAnnotation(outputDirAnnot)
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchStep(BeanNames.GENERATE_VEP_INPUT_STEP, jobParameters);

        assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

        assertTrue(vepInput.exists());
        assertEquals("20\t60343\t60343\tG/A\t+", readFirstLine(vepInput));
        assertEquals(EXPECTED_NON_ANNOTATED_VARIANTS, getLines(new FileInputStream(vepInput)));
    }
}
