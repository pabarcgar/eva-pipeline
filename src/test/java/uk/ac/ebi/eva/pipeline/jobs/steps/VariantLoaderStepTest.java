/*
 * Copyright 2016 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.ebi.eva.pipeline.jobs.steps;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.opencga.lib.common.Config;
import org.opencb.opencga.storage.core.StorageManagerFactory;
import org.opencb.opencga.storage.core.variant.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBIterator;
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
import uk.ac.ebi.eva.pipeline.jobs.GenotypedVcfJob;
import uk.ac.ebi.eva.test.configuration.BatchTestConfiguration;
import uk.ac.ebi.eva.test.rules.TemporaryMongoRule;
import uk.ac.ebi.eva.utils.EvaJobParameterBuilder;

import static org.junit.Assert.assertEquals;
import static uk.ac.ebi.eva.test.utils.JobTestUtils.count;
import static uk.ac.ebi.eva.utils.FileUtils.getResource;

/**
 * Test for {@link VariantLoaderStep}
 */
@RunWith(SpringRunner.class)
@ActiveProfiles({Application.VARIANT_WRITER_MONGO_PROFILE, Application.VARIANT_ANNOTATION_MONGO_PROFILE})
@TestPropertySource({"classpath:common-configuration.properties", "classpath:test-mongo.properties"})
@ContextConfiguration(classes = {GenotypedVcfJob.class, BatchTestConfiguration.class})
public class VariantLoaderStepTest {

    private static final int EXPECTED_VARIANTS = 300;

    private static final String SMALL_VCF_FILE = "/input-files/vcf/genotyped.vcf.gz";

    @Rule
    public TemporaryMongoRule mongoRule = new TemporaryMongoRule();

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    private String input;

    private static String opencgaHome = System.getenv("OPENCGA_HOME") != null ? System.getenv(
            "OPENCGA_HOME") : "/opt/opencga";

    @Test
    public void loaderStepShouldLoadAllVariants() throws Exception {
        Config.setOpenCGAHome(opencgaHome);
        String databaseName = mongoRule.getRandomTemporaryDatabaseName();

        // When the execute method in variantsLoad is executed
        JobParameters jobParameters = new EvaJobParameterBuilder()
                .collectionVariantsName("variants")
                .databaseName(databaseName)
                .inputStudyId("1")
                .inputVcf(input)
                .inputVcfAggregation("NONE")
                .inputVcfId("1")
                .toJobParameters();

        JobExecution jobExecution = jobLauncherTestUtils.launchStep(BeanNames.LOAD_VARIANTS_STEP, jobParameters);

        //Then variantsLoad step should complete correctly
        assertEquals(ExitStatus.COMPLETED, jobExecution.getExitStatus());
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

        // And the number of documents in the DB should be equals to the number of lines in the VCF file
        VariantStorageManager variantStorageManager = StorageManagerFactory.getVariantStorageManager();
        VariantDBAdaptor variantDBAdaptor = variantStorageManager.getDBAdaptor(databaseName, null);
        VariantDBIterator iterator = variantDBAdaptor.iterator(new QueryOptions());

        assertEquals(EXPECTED_VARIANTS, count(iterator));
    }

    @Before
    public void setUp() throws Exception {
        input = getResource(SMALL_VCF_FILE).getAbsolutePath();
    }

}
