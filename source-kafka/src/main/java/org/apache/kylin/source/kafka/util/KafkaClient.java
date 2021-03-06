/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.apache.kylin.source.kafka.util;

import com.google.common.collect.Maps;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kylin.cube.CubeInstance;
import org.apache.kylin.source.kafka.KafkaConfigManager;
import org.apache.kylin.source.kafka.config.BrokerConfig;
import org.apache.kylin.source.kafka.config.KafkaClusterConfig;
import org.apache.kylin.source.kafka.config.KafkaConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 */
public class KafkaClient {

    public static KafkaConsumer getKafkaConsumer(String brokers, String consumerGroup, Properties properties) {
        Properties props = constructDefaultKafkaConsumerProperties(brokers, consumerGroup, properties);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        return consumer;
    }

    public static KafkaProducer getKafkaProducer(String brokers, Properties properties) {
        Properties props = constructDefaultKafkaProducerProperties(brokers, properties);
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);
        return producer;
    }

    private static Properties constructDefaultKafkaProducerProperties(String brokers, Properties properties) {
        Properties props = new Properties();
        props.put("bootstrap.servers", brokers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "1");
        props.put("buffer.memory", 33554432);
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 50);
        props.put("timeout.ms", "30000");
        if (properties != null) {
            for (Map.Entry entry : properties.entrySet()) {
                props.put(entry.getKey(), entry.getValue());
            }
        }
        return props;
    }

    private static Properties constructDefaultKafkaConsumerProperties(String brokers, String consumerGroup, Properties properties) {
        Properties props = new Properties();
        props.put("bootstrap.servers", brokers);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("group.id", consumerGroup);
        props.put("session.timeout.ms", "30000");
        props.put("enable.auto.commit", "false");
        if (properties != null) {
            for (Map.Entry entry : properties.entrySet()) {
                props.put(entry.getKey(), entry.getValue());
            }
        }
        return props;
    }

    public static String getKafkaBrokers(KafkaConfig kafkaConfig) {
        String brokers = null;
        for (KafkaClusterConfig clusterConfig : kafkaConfig.getKafkaClusterConfigs()) {
            for (BrokerConfig brokerConfig : clusterConfig.getBrokerConfigs()) {
                if (brokers == null) {
                    brokers = brokerConfig.getHost() + ":" + brokerConfig.getPort();
                } else {
                    brokers = brokers + "," + brokerConfig.getHost() + ":" + brokerConfig.getPort();
                }
            }
        }
        return brokers;
    }

    public static long getEarliestOffset(KafkaConsumer consumer, String topic, int partitionId) {

        TopicPartition topicPartition = new TopicPartition(topic, partitionId);
        consumer.assign(Arrays.asList(topicPartition));
        consumer.seekToBeginning(Arrays.asList(topicPartition));

        return consumer.position(topicPartition);
    }

    public static long getLatestOffset(KafkaConsumer consumer, String topic, int partitionId) {

        TopicPartition topicPartition = new TopicPartition(topic, partitionId);
        consumer.assign(Arrays.asList(topicPartition));
        consumer.seekToEnd(Arrays.asList(topicPartition));

        return consumer.position(topicPartition);
    }

    public static Map<Integer, Long> getCurrentOffsets(final CubeInstance cubeInstance) {
        final KafkaConfig kafakaConfig = KafkaConfigManager.getInstance(cubeInstance.getConfig()).getKafkaConfig(cubeInstance.getFactTable());

        final String brokers = KafkaClient.getKafkaBrokers(kafakaConfig);
        final String topic = kafakaConfig.getTopic();

        Map<Integer, Long> startOffsets = Maps.newHashMap();
        try (final KafkaConsumer consumer = KafkaClient.getKafkaConsumer(brokers, cubeInstance.getName(), null)) {
            final List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
            for (PartitionInfo partitionInfo : partitionInfos) {
                long latest = KafkaClient.getLatestOffset(consumer, topic, partitionInfo.partition());
                startOffsets.put(partitionInfo.partition(), latest);
            }
        }
        return startOffsets;
    }
}
