/*
 * Copyright 2016 EMBL - European Bioinformatics Institute
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
package uk.ac.ebi.eva.pipeline.io.writers;

import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSourceEntry;
import org.opencb.opencga.storage.mongodb.variant.DBObjectToVariantConverter;
import org.opencb.opencga.storage.mongodb.variant.DBObjectToVariantSourceEntryConverter;
import org.opencb.opencga.storage.mongodb.variant.DBObjectToVariantStatsConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.data.MongoItemWriter;

import java.util.List;

/**
 * @author Diego Poggioli
 *
 * Write a list of {@link Variant} into MongoDB
 * See also {@link org.opencb.opencga.storage.mongodb.variant.VariantMongoDBWriter}
 *
 */
public class VariantMongoWriter extends MongoItemWriter<Variant> {
    private static final Logger logger = LoggerFactory.getLogger(VariantMongoWriter.class);

    private boolean includeStats;
    private String fileId;
    private int bulkSize = 0;
    private DBCollection variantsCollection;

    private BulkWriteOperation bulk;
    private long numVariantsWritten;
    private int currentBulkSize = 0;

    private DBObjectToVariantConverter variantConverter;
    private DBObjectToVariantStatsConverter statsConverter;
    private DBObjectToVariantSourceEntryConverter sourceEntryConverter;

    public VariantMongoWriter(boolean includeStats, String fileId, int bulkSize,
                              DBObjectToVariantConverter variantConverter, DBObjectToVariantStatsConverter statsConverter,
                              DBObjectToVariantSourceEntryConverter sourceEntryConverter, DBCollection variantsCollection) {
        this.includeStats = includeStats;
        this.fileId = fileId;
        this.bulkSize = bulkSize;
        this.variantConverter = variantConverter;
        this.statsConverter = statsConverter;
        this.sourceEntryConverter = sourceEntryConverter;
        this.variantsCollection = variantsCollection;

        resetBulk();
    }

    @Override
    protected void doWrite(List<? extends Variant> variants) {
        numVariantsWritten += variants.size();
        if(numVariantsWritten % 1000 == 0) {
            logger.info("Num variants written " + numVariantsWritten);
        }

        for (Variant variant : variants) {
            variant.setAnnotation(null);
            String id = variantConverter.buildStorageId(variant);

            for (VariantSourceEntry variantSourceEntry : variant.getSourceEntries().values()) {
                if (!variantSourceEntry.getFileId().equals(fileId)) {
                    continue;
                }

                // the chromosome and start appear just as shard keys, in an unsharded cluster they wouldn't be needed
                BasicDBObject query = new BasicDBObject("_id", id)
                        .append(DBObjectToVariantConverter.CHROMOSOME_FIELD, variant.getChromosome())
                        .append(DBObjectToVariantConverter.START_FIELD, variant.getStart());

                BasicDBObject addToSet = new BasicDBObject()
                        .append(DBObjectToVariantConverter.FILES_FIELD,
                                sourceEntryConverter.convertToStorageType(variantSourceEntry));

                if (includeStats) {
                    List<DBObject> sourceEntryStats = statsConverter.convertCohortsToStorageType(variantSourceEntry.getCohortStats(),
                            variantSourceEntry.getStudyId(), variantSourceEntry.getFileId());
                    addToSet.put(DBObjectToVariantConverter.STATS_FIELD, new BasicDBObject("$each", sourceEntryStats));
                }

                if (variant.getIds() != null && !variant.getIds().isEmpty()) {
                    addToSet.put(DBObjectToVariantConverter.IDS_FIELD, new BasicDBObject("$each", variant.getIds()));
                }

                BasicDBObject update = new BasicDBObject()
                        .append("$addToSet", addToSet)
                        .append("$setOnInsert", variantConverter.convertToStorageType(variant));    // assuming variantConverter.statsConverter == null

                bulk.find(query).upsert().updateOne(update);

                currentBulkSize++;
            }

            if (currentBulkSize >= bulkSize) {
                executeBulk();
            }
        }

        //cover small variant set with size < bulk size
        if(currentBulkSize<bulkSize){
            executeBulk();
        }

    }

    private void executeBulk() {
        if(currentBulkSize != 0){
            logger.debug("Execute bulk. BulkSize : " + currentBulkSize);
            bulk.execute();
            resetBulk();
        }

    }

    private void resetBulk() {
        bulk = variantsCollection.initializeUnorderedBulkOperation();
        currentBulkSize = 0;
    }

}
