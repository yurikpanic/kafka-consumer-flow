package com.elsyton.kafka

import java.util.Properties

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.testkit.{EventFilter, TestKit}
import com.elsyton.docker.KafkaService
import com.elsyton.kafka.ConsumerFlow.ConsumerCommand
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{LongDeserializer, LongSerializer, StringDeserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

class ConsumerFlowTest(_system: ActorSystem)
    extends TestKit(_system)
    with FlatSpecLike
    with BeforeAndAfterAll
    with KafkaService {

  def this() =
    this(
      ActorSystem(
        "consumer-flow-test",
        ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]""")))

  implicit val mat = ActorMaterializer()

  override def topicDefinitions: List[String] = "test1:1:1" :: "test2:1:1" :: super.topicDefinitions

  private lazy val producer: KafkaProducer[java.lang.Long, String] = {
    val props = new Properties()
    props.put("bootstrap.servers", s"localhost:$kafkaPort")
    props.put("acks", "all")
    new KafkaProducer[java.lang.Long, String](props, new LongSerializer, new StringSerializer)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    startKafkaServer()
    println(s"kafka port is $kafkaPort")
  }

  override protected def afterAll(): Unit = {
    producer.close()
    TestKit.shutdownActorSystem(_system)
    stopAllQuietly()
    super.afterAll()
  }

  private def newConsumer(topic: String, groupId: String = "test"): KafkaConsumer[java.lang.Long, String] = {
    val props = new Properties()
    props.put("bootstrap.servers", s"localhost:$kafkaPort")
    props.put("group.id", groupId)
    props.put("enable.auto.commit", "false")
    new KafkaConsumer[java.lang.Long, String](props, new LongDeserializer, new StringDeserializer)
  }

  it should "stream data from KafkaConsumer already subscribed to topics" in {
    val topic       = "test1"
    val numMessages = 10

    (1L to numMessages).foreach { i: Long =>
      producer.send(new ProducerRecord[java.lang.Long, String](topic, i, s"message$i"))
    }

    val consumer = newConsumer(topic, topic)

    val tp = Seq(new TopicPartition(topic, 0)).asJava
    consumer.assign(tp)
    consumer.seekToBeginning(tp)

    val resF = Source
      .actorRef[ConsumerCommand[java.lang.Long, String]](100, OverflowStrategy.fail)
      .via(new ConsumerFlow(consumer))
      .take(numMessages)
      .runWith(Sink.seq)

    val res = Await.result(resF, 10.seconds)

    assertResult(numMessages)(res.length)
    assertResult(1L to numMessages)(res.map(_.key()))
    assertResult((1L to numMessages).map(i => s"message$i"))(res.map(_.value()))
  }

  it should "stream data after KafkaConsumer is subscribed to some topics through ConsumerCommand" in {
    val topic       = "test2"
    val numMessages = 10

    val consumer = newConsumer(topic, topic)

    val commandActorPromise = Promise[ActorRef]

    val pollingStopped = EventFilter.info(start = "not subscribed to any topic", occurrences = 1)

    val pollingResumed = EventFilter.info(start = "polling consumer resumed", occurrences = 1)

    val resF = Source
      .actorRef[ConsumerCommand[java.lang.Long, String]](100, OverflowStrategy.fail)
      .mapMaterializedValue(commandActorPromise.success)
      .via(new ConsumerFlow(consumer))
      .take(numMessages)
      .runWith(Sink.seq)
    pollingStopped.intercept(())

    (1L to numMessages).foreach { i: Long =>
      producer.send(new ProducerRecord[java.lang.Long, String](topic, i, s"message$i"))
    }

    val commandActorRef = Await.result(commandActorPromise.future, 3.seconds)

    commandActorRef ! { consumer: KafkaConsumer[java.lang.Long, String] =>
      val tp = Seq(new TopicPartition(topic, 0)).asJava
      consumer.assign(tp)
      consumer.seekToBeginning(tp)
      ()
    }

    pollingResumed.intercept(())

    val res = Await.result(resF, 10.seconds)

    assertResult(numMessages)(res.length)
    assertResult(1L to numMessages)(res.map(_.key()))
    assertResult((1L to numMessages).map(i => s"message$i"))(res.map(_.value()))
  }

  it should "consume all functions to configure kafka consumer" in {
    val topic = "test1"

    val consumer = newConsumer(topic, topic)

    val commandActorRef = Source
      .actorRef[ConsumerCommand[java.lang.Long, String]](100, OverflowStrategy.fail)
      .via(new ConsumerFlow(consumer))
      .to(Sink.ignore)
      .run()

    val promise1 = Promise[Unit]
    commandActorRef ! { consumer: KafkaConsumer[java.lang.Long, String] =>
      promise1.success(())
      ()
    }

    val promise2 = Promise[Unit]
    commandActorRef ! { consumer: KafkaConsumer[java.lang.Long, String] =>
      promise2.success(())
      ()
    }

    Await.result(promise1.future, 1.second)
    Await.result(promise2.future, 1.second)
  }

}
