/**
 * Copyright (C) 2012 Microsoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoft.wake.test.remote;

import com.microsoft.wake.EventHandler;
import com.microsoft.wake.impl.LoggingEventHandler;
import com.microsoft.wake.impl.LoggingUtils;
import com.microsoft.wake.impl.TimerStage;
import com.microsoft.wake.remote.*;
import com.microsoft.wake.remote.impl.DefaultRemoteIdentifierFactoryImplementation;
import com.microsoft.wake.remote.impl.DefaultRemoteManagerImplementation;
import com.microsoft.wake.remote.impl.MultiCodec;
import com.microsoft.wake.remote.impl.ObjectSerializableCodec;
import com.microsoft.wake.test.util.Monitor;
import com.microsoft.wake.test.util.TimeoutHandler;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class RemoteManagerTest {

  @Rule
  public final TestName name = new TestName();

  final String logPrefix = "TEST ";
  final int port = 9100;

  @Test
  public void testRemoteManagerTest() throws Exception {
    System.out.println(logPrefix + name.getMethodName());
    LoggingUtils.setLoggingLevel(Level.INFO);

    Monitor monitor = new Monitor();
    TimerStage timer = new TimerStage(new TimeoutHandler(monitor), 2000, 2000);

    Map<Class<?>, Codec<?>> clazzToCodecMap = new HashMap<Class<?>, Codec<?>>();
    clazzToCodecMap.put(StartEvent.class, new ObjectSerializableCodec<StartEvent>());
    clazzToCodecMap.put(TestEvent.class, new ObjectSerializableCodec<TestEvent>());
    clazzToCodecMap.put(TestEvent1.class, new ObjectSerializableCodec<TestEvent1>());
    clazzToCodecMap.put(TestEvent2.class, new ObjectSerializableCodec<TestEvent2>());
    Codec<?> codec = new MultiCodec<Object>(clazzToCodecMap);

    String hostAddress = NetUtils.getLocalAddress();

    RemoteManager rm = new DefaultRemoteManagerImplementation("name", hostAddress, port, codec, new LoggingEventHandler<Throwable>(), false);
    RemoteIdentifierFactory factory = new DefaultRemoteIdentifierFactoryImplementation();
    RemoteIdentifier remoteId = factory.getNewInstance("socket://" + hostAddress + ":" + port);
    Assert.assertTrue(rm.getMyIdentifier().equals(remoteId));

    EventHandler<StartEvent> proxyConnection = rm.getHandler(remoteId, StartEvent.class);
    EventHandler<TestEvent1> proxyHandler1 = rm.getHandler(remoteId, TestEvent1.class);
    EventHandler<TestEvent2> proxyHandler2 = rm.getHandler(remoteId, TestEvent2.class);

    AtomicInteger counter = new AtomicInteger(0);
    int finalSize = 2;
    rm.registerHandler(StartEvent.class, new MessageTypeEventHandler<StartEvent>(rm, monitor, counter, finalSize));


    proxyConnection.onNext(new StartEvent());

    monitor.mwait();
    proxyHandler1.onNext(new TestEvent1("hello1", 0.0));// registration after send expected to fail
    proxyHandler2.onNext(new TestEvent2("hello2", 1.0));


    monitor.mwait();

    Assert.assertEquals(finalSize, counter.get());

    rm.close();
    timer.close();
  }

  @Test
  public void testRemoteManagerOrderingGuaranteeTest() throws Exception {
    System.out.println(logPrefix + name.getMethodName());
    LoggingUtils.setLoggingLevel(Level.INFO);

    Monitor monitor = new Monitor();
    TimerStage timer = new TimerStage(new TimeoutHandler(monitor), 2000, 2000);

    Map<Class<?>, Codec<?>> clazzToCodecMap = new HashMap<Class<?>, Codec<?>>();
    clazzToCodecMap.put(StartEvent.class, new ObjectSerializableCodec<StartEvent>());
    clazzToCodecMap.put(TestEvent.class, new ObjectSerializableCodec<TestEvent>());
    clazzToCodecMap.put(TestEvent1.class, new ObjectSerializableCodec<TestEvent1>());
    clazzToCodecMap.put(TestEvent2.class, new ObjectSerializableCodec<TestEvent2>());
    Codec<?> codec = new MultiCodec<Object>(clazzToCodecMap);

    String hostAddress = NetUtils.getLocalAddress();

    RemoteManager rm = new DefaultRemoteManagerImplementation("name", hostAddress, port, codec, new LoggingEventHandler<Throwable>(), true);
    RemoteIdentifierFactory factory = new DefaultRemoteIdentifierFactoryImplementation();
    RemoteIdentifier remoteId = factory.getNewInstance("socket://" + hostAddress + ":" + port);

    EventHandler<StartEvent> proxyConnection = rm.getHandler(remoteId, StartEvent.class);
    EventHandler<TestEvent1> proxyHandler1 = rm.getHandler(remoteId, TestEvent1.class);
    EventHandler<TestEvent2> proxyHandler2 = rm.getHandler(remoteId, TestEvent2.class);

    AtomicInteger counter = new AtomicInteger(0);
    int finalSize = 2;
    rm.registerHandler(StartEvent.class, new MessageTypeEventHandler<StartEvent>(rm, monitor, counter, finalSize));

    proxyConnection.onNext(new StartEvent());

    monitor.mwait();

    proxyHandler1.onNext(new TestEvent1("hello1", 0.0));
    proxyHandler2.onNext(new TestEvent2("hello2", 1.0));

    monitor.mwait();

    Assert.assertEquals(finalSize, counter.get());

    rm.close();
    timer.close();
  }

  @Test
  public void testRemoteManagerPBufTest() throws Exception {
    System.out.println(logPrefix + name.getMethodName());
    LoggingUtils.setLoggingLevel(Level.INFO);

    Monitor monitor = new Monitor();
    TimerStage timer = new TimerStage(new TimeoutHandler(monitor), 2000, 2000);

    Map<Class<?>, Codec<?>> clazzToCodecMap = new HashMap<Class<?>, Codec<?>>();
    clazzToCodecMap.put(TestEvent.class, new TestEventCodec());
    Codec<?> codec = new MultiCodec<Object>(clazzToCodecMap);

    String hostAddress = NetUtils.getLocalAddress();

    RemoteManager rm = new DefaultRemoteManagerImplementation("name", hostAddress, port, codec, new LoggingEventHandler<Throwable>(), false);

    RemoteIdentifierFactory factory = new DefaultRemoteIdentifierFactoryImplementation();
    RemoteIdentifier remoteId = factory.getNewInstance("socket://" + hostAddress + ":" + port);

    EventHandler<TestEvent> proxyHandler = rm.getHandler(remoteId, TestEvent.class);

    AtomicInteger counter = new AtomicInteger(0);
    int finalSize = 0;
    rm.registerHandler(TestEvent.class, new MessageTypeEventHandler<TestEvent>(rm, monitor, counter, finalSize));

    proxyHandler.onNext(new TestEvent("hello", 0.0));

    monitor.mwait();

    Assert.assertEquals(finalSize, counter.get());

    rm.close();
    timer.close();
  }

  @Test
  public void testRemoteManagerExceptionTest() {
    System.out.println(logPrefix + name.getMethodName());
    LoggingUtils.setLoggingLevel(Level.INFO);

    Monitor monitor = new Monitor();
    TimerStage timer = new TimerStage(new TimeoutHandler(monitor), 2000, 2000);

    Map<Class<?>, Codec<?>> clazzToCodecMap = new HashMap<Class<?>, Codec<?>>();
    clazzToCodecMap.put(StartEvent.class, new ObjectSerializableCodec<StartEvent>());
    clazzToCodecMap.put(TestEvent.class, new ObjectSerializableCodec<TestEvent>());
    Codec<?> codec = new MultiCodec<Object>(clazzToCodecMap);

    String hostAddress = NetUtils.getLocalAddress();

    ExceptionHandler errorHandler = new ExceptionHandler(monitor);
    try (RemoteManager rm = new DefaultRemoteManagerImplementation("name", hostAddress, port, codec, errorHandler, false)) {
      RemoteIdentifierFactory factory = new DefaultRemoteIdentifierFactoryImplementation();
      RemoteIdentifier remoteId = factory.getNewInstance("socket://" + hostAddress + ":" + port);

      EventHandler<StartEvent> proxyConnection = rm.getHandler(remoteId, StartEvent.class);
      rm.registerHandler(StartEvent.class, new ExceptionGenEventHandler<StartEvent>("recvExceptionGen"));

      proxyConnection.onNext(new StartEvent());
      monitor.mwait();
      timer.close();

    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  class MessageTypeEventHandler<T> implements EventHandler<RemoteMessage<T>> {

    private final RemoteManager rm;
    private final Monitor monitor;
    private final AtomicInteger counter;
    private final int finalSize;

    MessageTypeEventHandler(RemoteManager rm, Monitor monitor, AtomicInteger counter, int finalSize) {
      this.rm = rm;
      this.monitor = monitor;
      this.counter = counter;
      this.finalSize = finalSize;
    }

    @Override
    public void onNext(RemoteMessage<T> value) {

      RemoteIdentifier id = value.getIdentifier();
      T message = value.getMessage();

      System.out.println(this.getClass() + " " + value + " " + id.toString() + " " + message.toString());

      System.out.println("Sleeping to force a bug");
      // try {
      //   Thread.sleep(2000);
      //} catch (InterruptedException e) {

      //  e.printStackTrace();
      // }

      // register specific handlers
      rm.registerHandler(id, TestEvent1.class, new ConsoleEventHandler<TestEvent1>("console1", monitor, counter, finalSize));
      rm.registerHandler(id, TestEvent2.class, new ConsoleEventHandler<TestEvent2>("console2", monitor, counter, finalSize));
      monitor.mnotify();
    }

  }

  class ConsoleEventHandler<T> implements EventHandler<T> {

    private final String name;
    private final Monitor monitor;
    private final AtomicInteger counter;
    private final int finalSize;

    ConsoleEventHandler(String name, Monitor monitor, AtomicInteger counter, int finalSize) {
      this.name = name;
      this.monitor = monitor;
      this.counter = counter;
      this.finalSize = finalSize;
    }

    @Override
    public void onNext(T value) {
      System.out.println(this.getClass() + " " + name + " " + value);
      if (counter.incrementAndGet() == finalSize) {
        System.out.println(this.getClass() + " notify counter: " + counter.get());
        monitor.mnotify();
      }
    }
  }

  class ExceptionGenEventHandler<T> implements EventHandler<RemoteMessage<T>> {

    private final String name;

    ExceptionGenEventHandler(final String name) {
      this.name = name;
    }

    @Override
    public void onNext(RemoteMessage<T> value) {
      System.out.println(name + " " + value);
      throw new TestRuntimeException("Test exception");
    }

  }

  class ExceptionHandler implements EventHandler<Throwable> {

    private final Monitor monitor;

    ExceptionHandler(final Monitor monitor) {
      this.monitor = monitor;
    }

    @Override
    public void onNext(Throwable value) {
      System.out.println("!!! ExceptionHandler called : " + value);
      monitor.mnotify();
    }

  }

  final class TestRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TestRuntimeException(final String s) {
      super(s);
    }
  }

}
