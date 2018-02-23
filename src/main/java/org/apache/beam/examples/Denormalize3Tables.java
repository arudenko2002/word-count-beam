package org.apache.beam.examples;


import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.transforms.join.CoGbkResult;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An example that joins together 3 tables
 * By Alexey Rudenko
 */
public class Denormalize3Tables {

    public static void main(String[] args) {
        System.out.println("Start process");

        // Create a PipelineOptions object.
        PipelineOptions options = PipelineOptionsFactory.fromArgs(args).create();
        System.out.println(options.getJobName());
        System.out.println(options.getRunner());
        System.out.println(options.getTempLocation());
        //options.setRunner(DataflowRunner.class);

        //--project=MyProject (simple property, will set the "project" property to "MyProject")
        //        --readOnly=true (for boolean properties, will set the "readOnly" property to "true")
        //--readOnly (shorthand for boolean properties, will set the "readOnly" property to "true")
        //--x=1 --x=2 --x=3 (list style simple property, will set the "x" property to [1, 2, 3])
        //--x=1,2,3 (shorthand list style simple property, will set the "x" property to [1, 2, 3])
        //--complexObject='{"key1":"value1",...} (JSON format for all other complex types)


        //options.setProject("my-project-id");
        options.setTempLocation("gs://staging-directory");
        //options.setRunner(DataflowRunner.class);

        // Create the Pipeline object with the options we defined above.
        Pipeline p = Pipeline.create(options);

        PCollection<KV<String, String>> set1key1 = p
                .apply(TextIO.read().from("gs://source-directory/streams.gz"))
                .apply("set1key1", ParDo.of(new DoFn<String,KV<String,String>>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        String line = c.element();
                        try {
                            JSONObject obj = new JSONObject(line);
                            String key = obj.getString("track_id");
                            c.output(KV.of(key, line));
                        } catch(JSONException e) {
                            System.out.println("not json line! "+line);;
                        }
                    }
                }))
                ;

        PCollection<KV<String, String>> set2key1 = p
                .apply(TextIO.read().from("gs://source-directory/tracks.gz"))
                .apply("set2key1", ParDo.of(new DoFn<String,KV<String,String>>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        String line = c.element();
                        try {
                            JSONObject obj = new JSONObject(line);
                            String key = obj.getString("track_id");
                            c.output(KV.of(key, line));
                        } catch(JSONException e) {
                            System.out.println("not json line! "+line);;
                        }
                    }
                }))
                ;

        PCollection<KV<String, String>> set3key2 = p
                .apply(TextIO.read().from("gs://source-directory/users.gz"))
                .apply("set3key2", ParDo.of(new DoFn<String,KV<String,String>>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        String line = c.element();
                        try {
                            JSONObject obj = new JSONObject(line);
                            String key = obj.getString("user_id");
                            c.output(KV.of(key, line));
                        } catch(JSONException e) {
                            System.out.println("not json line! "+line);;
                        }
                    }
                }))
                ;

        //Put together 1 and 2 tables
        final TupleTag<String> tag_set1 = new TupleTag<String>();
        final TupleTag<String> tag_set2 = new TupleTag<String>();

        KeyedPCollectionTuple<String> coGbkInput = KeyedPCollectionTuple
                .of(tag_set1, set1key1)
                .and(tag_set2, set2key1);

        PCollection<KV<String, CoGbkResult>> set1_2 =
                coGbkInput.apply("CoGroupByKey", CoGroupByKey.<String>create());

        PCollection<KV<String, String>> set1_2_key2 = set1_2
                .apply("set1_2_key2",ParDo.of(
                        new DoFn<KV<String, CoGbkResult>, KV<String, String>>() {
                            @ProcessElement
                            public void processElement(ProcessContext c) {
                                String set_2 = c.element().getValue().getOnly(tag_set2);
                                for (String set_1
                                        : c.element().getValue().getAll(tag_set1)) {
                                    try {
                                        JSONObject obj1 = new JSONObject(set_1);
                                        JSONObject obj2 = new JSONObject(set_2);
                                        String key2 = obj1.getString("user_id");
                                        obj1.put("isrc",obj2.getString("isrc"));
                                        obj1.put("album_code",obj2.getString("album_code"));
                                        String set_1_isrc_album_code = obj1.toString();
                                        c.output(KV.of(key2, set_1_isrc_album_code));
                                    } catch(JSONException e) {
                                        System.out.println("Error to add field to JSON!Ignoring the line..");
                                    }
                                }
                            }
                        }));
        // Put together 1_2 and 3 tables
        final TupleTag<String> tag_set1_2 = new TupleTag<String>();
        final TupleTag<String> tag_set3 = new TupleTag<String>();

        KeyedPCollectionTuple<String> coGbkInput2 = KeyedPCollectionTuple
                .of(tag_set1_2, set1_2_key2)
                .and(tag_set3, set3key2);

        PCollection<KV<String, CoGbkResult>> set1_2_3 =
                coGbkInput2.apply("CoGroupByKey2", CoGroupByKey.<String>create());

        PCollection<String> set_join_1_2_3 = set1_2_3
                .apply("set1_2_3",ParDo.of(
                        new DoFn<KV<String, CoGbkResult>, String>() {
                            @ProcessElement
                            public void processElement(ProcessContext c) {
                                String set_3 = c.element().getValue().getOnly(tag_set3);
                                for (String set_1
                                        : c.element().getValue().getAll(tag_set1_2)) {
                                    try {
                                        JSONObject obj1 = new JSONObject(set_1);
                                        JSONObject obj3 = new JSONObject(set_3);
                                        String key="product";obj1.put(key, obj3.get(key));
                                        key="country";obj1.put(key, obj3.get(key));
                                        key="region";obj1.put(key, obj3.get(key));
                                        key="zip_code";obj1.put(key, obj3.get(key));
                                        key="access";obj1.put(key, obj3.get(key));
                                        key="gender";obj1.put(key, obj3.get(key));
                                        key="partner";obj1.put(key, obj3.get(key));
                                        key="referral";obj1.put(key, obj3.get(key));
                                        key="type";obj1.put(key, obj3.get(key));
                                        key="birth_year";obj1.put(key, obj3.get(key));
                                        String set_all = obj1.toString();
                                        c.output(set_all);
                                    } catch(JSONException e) {
                                        System.out.println("Error to add filed to the Json!");
                                    }
                                }
                            }
                        }));

        set_join_1_2_3.apply(TextIO.write().to("gs://source-directory/streams_denorm.json"));

        // Run the pipeline.
        p.run().waitUntilFinish();
        System.out.println("End of process");
    }
}
