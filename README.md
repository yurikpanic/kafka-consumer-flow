# kafka-consumer-flow

A graph stage for akka streams which is constructed from already existing KafkaConsumer.
It polls KafkaConsumer and emits downstream ConsumerRecords received.

The upstream - is a stream of ConsumerCommand. ConsumerCommand - is a functions which is called between polls of KafkaConsumer.
This function is a way to modify KafkaConsumer behaviour "on the fly". E.g. change topic subscriptions, fetch topic metadata etc.

So this is a very thin wrapper over KafkaConsumer API with full access to any of it functions. And a gateway to akka streams with shared KafkaConsumer.

The output stream of this stage then can be processed using all akka streams machinery. E.g. spliting it to multiple streams using ```partition``` function, but keeping single KafkaConsumer for reading from kafka.
