package com.elsyton.docker

import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.{ContainerLink, DockerContainer, DockerReadyChecker, VolumeMapping}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

trait KafkaService extends DockerKitSpotify {

  def topicDefinitions: List[String] = List("test-topic:1:1")

  override val StartContainersTimeout: FiniteDuration = 60.seconds

  private def randomStr = new String(Random.alphanumeric.take(10).toArray)

  private val zookeeperContainer = DockerContainer("zookeeper:latest", name = Some(s"zookeeper_$randomStr"))
    .withPorts(2181 -> None)
    .withReadyChecker(DockerReadyChecker.LogLineContains("binding to port 0.0.0.0/0.0.0.0:2181"))

  private val kafkaEnv = Seq(
    "KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181/kafka",
    s"KAFKA_ADVERTISED_HOST_NAME=${java.net.InetAddress.getLocalHost.getCanonicalHostName}" /*,
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE=false"*/ ) ++ {
    val topicsStr = topicDefinitions.mkString(",")
    if (topicsStr.isEmpty) Seq.empty
    else Seq(s"KAFKA_CREATE_TOPICS=$topicsStr")
  }

  private val kafkaContainer = DockerContainer("wurstmeister/kafka:latest", name = Some(s"kafka_$randomStr"))
    .withEnv(kafkaEnv: _*)
    .withPorts(9092 -> None)
    .withVolumes(Seq(VolumeMapping("/var/run/docker.sock", "/var/run/docker.sock", rw = true)))
    .withReadyChecker(DockerReadyChecker.LogLineContains("Created topic \"test-topic\""))
    .withLinks(ContainerLink(zookeeperContainer, "zookeeper"))

  lazy val kafkaPort: Int =
    Await.result(kafkaContainer.getPorts().map(_(9092)), 3.seconds)

  def startKafkaServer(): Unit = {
    startAllOrFail()
    assert(Await.result(containerManager.isReady(kafkaContainer), StartContainersTimeout))
  }

  override def dockerContainers: List[DockerContainer] =
    kafkaContainer :: zookeeperContainer :: super.dockerContainers
}
