package com.atmytrack.app
import com.atmytrack.app.audio.LatestCommand
import org.junit.Test
import org.junit.Assert.*
class LatestCommandTest {
 @Test fun thousandsOfUpdatesKeepOneQueuedTargetAndOneReplacement() {
  val queue=java.util.ArrayDeque<()->Unit>();val applied=mutableListOf<Int>();lateinit var latest:LatestCommand<Int>
  latest=LatestCommand({queue.add(it)}) { value -> applied+=value;if(value==9999)repeat(10000){latest.offer(10000+it)} }
  repeat(10000){latest.offer(it)};assertEquals(1,queue.size);queue.removeFirst()();assertEquals(listOf(9999),applied);assertEquals(1,queue.size)
  queue.removeFirst()();assertEquals(listOf(9999,19999),applied);assertTrue(queue.isEmpty())
  latest.offer(1);latest.clear();queue.removeFirst()();assertEquals(2,applied.size)
 }
}
