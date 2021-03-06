/*
 * Copyright 2017 Ivan Krizsan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.ivankrizsan.springintegration.channelinterceptors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.FluxMessageChannel;
import org.springframework.integration.channel.PriorityChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import se.ivankrizsan.springintegration.channelinterceptors.helpers
    .ExecutorChannelLoggingAndCountingInterceptor;
import se.ivankrizsan.springintegration.messagechannels.helpers.MyReactiveSubscriber;
import se.ivankrizsan.springintegration.shared.EmptyConfiguration;
import se.ivankrizsan.springintegration.shared.SpringIntegrationExamplesConstants;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Exercises demonstrating the use of the Spring Integration executor channel interceptor.
 *
 * @author Ivan Krizsan
 * @see ExecutorChannelInterceptor
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@EnableIntegration
@ContextConfiguration(classes = { EmptyConfiguration.class })
public class ExecutorChannelInterceptorTests implements SpringIntegrationExamplesConstants {
    /* Constant(s): */
    protected static final Log LOGGER = LogFactory.getLog(ExecutorChannelInterceptorTests.class);

    /* Instance variable(s): */
    @Autowired
    protected BeanFactory mBeanFactory;


    /**
     * Tests creating an executor message channel with one subscriber and an executor
     * channel interceptor registered on the channel. A message is then sent to the channel.
     *
     * Expected result: Sending message to the channel should be intercepted.
     * Message handling should be intercepted both before and after handling of a message.
     * Receiving messages from the message channel should not be intercepted.
     *
     * @throws Exception If an error occurs. Indicates test failure.
     */
    @Test
    public void executorChannelWithExecutorChannelInterceptorTest() throws Exception {
        final ThreadPoolTaskExecutor theExecutorChannelExecutor;
        final ExecutorChannel theExecutorChannel;
        final Message<String> theInputMessage;
        final List<Message> theSubscriberReceivedMessages =
            new CopyOnWriteArrayList<>();
        final ExecutorChannelLoggingAndCountingInterceptor theLoggingAndCountingChannelInterceptor;

        theInputMessage = MessageBuilder.withPayload(GREETING_STRING).build();

        // <editor-fold desc="Answer Section" defaultstate="collapsed">
        /*
         * Create and initialize the executor that the executor channel will
         * use for message dispatching.
         */
        theExecutorChannelExecutor = new ThreadPoolTaskExecutor();
        theExecutorChannelExecutor.initialize();

        theExecutorChannel = new ExecutorChannel(theExecutorChannelExecutor);
        theExecutorChannel.setComponentName(EXECUTOR_CHANNEL_NAME);

        /* Create the logging and counting interceptor and add it to the message channel. */
        theLoggingAndCountingChannelInterceptor =
            new ExecutorChannelLoggingAndCountingInterceptor();
        theExecutorChannel.addInterceptor(theLoggingAndCountingChannelInterceptor);
        theExecutorChannel.setBeanFactory(mBeanFactory);
        theExecutorChannel.onInit();

        /*
         * Create a subscriber (message handler) that adds each received message
         * to a list. Register the subscriber with the subscribable message channel.
         */
        final MessageHandler theSubscriber = theSubscriberReceivedMessages::add;
        theExecutorChannel.subscribe(theSubscriber);

        theExecutorChannel.send(theInputMessage);
        // </editor-fold>
        await().atMost(2, TimeUnit.SECONDS).until(() -> theSubscriberReceivedMessages.size() > 0);

        Assert.assertTrue("A single message should have been received by subscriber",
            theSubscriberReceivedMessages.size() == 1);

        /* Sending of message should have been intercepted. */
        Assert.assertEquals(
            "Message sending should have been intercepted before"
                + " the message being sent", 1,
            theLoggingAndCountingChannelInterceptor.getPreSendMessageCount());
        Assert.assertEquals("Message sending should have been intercepted "
                + "after the message having been sent", 1,
            theLoggingAndCountingChannelInterceptor.getPostSendMessageCount());
        Assert.assertEquals("Message sending should have completed", 1,
            theLoggingAndCountingChannelInterceptor.getAfterSendCompletionMessageCount());

        /* Handling of message should have been intercepted. */
        Assert.assertEquals("Message handling should have been intercepted "
                + "before message was handled",
            1, theLoggingAndCountingChannelInterceptor.getBeforeHandleMessageCount());
        Assert.assertEquals("Message handling should have been intercepted "
                + "after message was handled", 1,
            theLoggingAndCountingChannelInterceptor.getAfterMessageHandledMessageCount());

        /* Receiving of message should not have been intercepted. */
        Assert.assertEquals(
            "Receiving will not be intercepted by message channels with subscribers",
            0, theLoggingAndCountingChannelInterceptor.getPreReceiveMessageCount());
        Assert.assertEquals(
            "Receiving will not be intercepted by message channels with subscribers",
            0, theLoggingAndCountingChannelInterceptor.getPostReceiveMessageCount());
        Assert.assertEquals(
            "Receiving will not be intercepted by message channels with subscribers",
            0, theLoggingAndCountingChannelInterceptor.getAfterReceiveCompletionMessageCount());
    }

    /**
     * Tests creating a direct message channel with a subscriber and an executor channel
     * interceptor registered on the channel. A message is then sent to the channel.
     *
     * Expected result: Sending message to the channel should be intercepted.
     * Receiving message from the channel should not be intercepted.
     * Message handling should not be intercepted.
     *
     * @throws Exception If an error occurs. Indicates test failure.
     */
    @Test
    public void directChannelWithExecutorChannelInterceptorTest() throws Exception {
        final DirectChannel theDirectChannel;
        final Message<String> theInputMessage;
        final List<Message> theSubscriberReceivedMessages =
            new CopyOnWriteArrayList<>();
        final ExecutorChannelLoggingAndCountingInterceptor theLoggingAndCountingChannelInterceptor;

        theInputMessage = MessageBuilder.withPayload(GREETING_STRING).build();

        // <editor-fold desc="Answer Section" defaultstate="collapsed">
        theDirectChannel = new DirectChannel();
        theDirectChannel.setComponentName(DIRECT_CHANNEL_NAME);

        theLoggingAndCountingChannelInterceptor =
            new ExecutorChannelLoggingAndCountingInterceptor();
        theDirectChannel.addInterceptor(theLoggingAndCountingChannelInterceptor);

        /*
         * Create a subscriber (message handler) that adds each received message
         * to a list. Register the subscriber with the subscribable message channel.
         */
        final MessageHandler theSubscriber = theSubscriberReceivedMessages::add;
        theDirectChannel.subscribe(theSubscriber);

        theDirectChannel.send(theInputMessage);

        // </editor-fold>
        await().atMost(2, TimeUnit.SECONDS).until(() -> theSubscriberReceivedMessages.size() > 0);

        Assert.assertTrue("A single message should have been received",
            theSubscriberReceivedMessages.size() == 1);

        /* Sending of message should have been intercepted. */
        verifyDirectMessageChannelInterception(theLoggingAndCountingChannelInterceptor);
    }

    /**
     * Tests creating a publish-subscribe message channel with one subscriber and an executor
     * channel interceptor registered on the channel. A message is then sent to the channel.
     *
     * Expected result: Sending message to the channel should be intercepted.
     * Message handling should not be intercepted.
     * Receiving messages from the message channel should not be intercepted.
     *
     * @throws Exception If an error occurs. Indicates test failure.
     */
    @Test
    public void publishSubscribeChannelWithExecutorChannelInterceptorTest() throws Exception {
        final PublishSubscribeChannel thePublishSubscribeChannel;
        final Message<String> theInputMessage;
        final List<Message> theSubscriberReceivedMessages =
            new CopyOnWriteArrayList<>();
        final ExecutorChannelLoggingAndCountingInterceptor theLoggingAndCountingChannelInterceptor;

        theInputMessage = MessageBuilder.withPayload(GREETING_STRING).build();

        // <editor-fold desc="Answer Section" defaultstate="collapsed">
        thePublishSubscribeChannel = new PublishSubscribeChannel();
        thePublishSubscribeChannel.setComponentName(PUBSUB_CHANNEL_NAME);

        /* Create the logging and counting interceptor and add it to the message channel. */
        theLoggingAndCountingChannelInterceptor =
            new ExecutorChannelLoggingAndCountingInterceptor();
        thePublishSubscribeChannel.addInterceptor(theLoggingAndCountingChannelInterceptor);
        thePublishSubscribeChannel.setBeanFactory(mBeanFactory);
        thePublishSubscribeChannel.onInit();

        /*
         * Create a subscriber (message handler) that adds each received message
         * to a list. Register the subscriber with the subscribable message channel.
         */
        final MessageHandler theSubscriber = theSubscriberReceivedMessages::add;
        thePublishSubscribeChannel.subscribe(theSubscriber);

        thePublishSubscribeChannel.send(theInputMessage);
        // </editor-fold>
        await().atMost(2, TimeUnit.SECONDS).until(() -> theSubscriberReceivedMessages.size() > 0);

        Assert.assertTrue("A single message should have been received by subscriber",
            theSubscriberReceivedMessages.size() == 1);

        verifyDirectMessageChannelInterception(theLoggingAndCountingChannelInterceptor);
    }

    /**
     * Tests creating a flux message channel with a subscriber and an executor channel
     * interceptor registered on the channel. A message is then sent to the channel.
     *
     * Expected result: Sending message to the channel should be intercepted.
     * Receiving message from the channel should not be intercepted.
     * Message handling should not be intercepted.
     *
     * @throws Exception If an error occurs. Indicates test failure.
     */
    @Test
    public void fluxChannelWithExecutorChannelInterceptorTest() throws Exception {
        final FluxMessageChannel theFluxMessageChannel;
        final Message<String> theInputMessage;
        final ExecutorChannelLoggingAndCountingInterceptor theLoggingAndCountingChannelInterceptor;

        theInputMessage = MessageBuilder.withPayload(GREETING_STRING).build();

        // <editor-fold desc="Answer Section" defaultstate="collapsed">
        theFluxMessageChannel = new FluxMessageChannel();
        theFluxMessageChannel.setComponentName(FLUX_CHANNEL_NAME);

        theLoggingAndCountingChannelInterceptor =
            new ExecutorChannelLoggingAndCountingInterceptor();
        theFluxMessageChannel.addInterceptor(theLoggingAndCountingChannelInterceptor);

        /* Create a subscriber and subscribe to the message channel. */
        final MyReactiveSubscriber theSubscriber =
            new MyReactiveSubscriber("First subscriber");
        theFluxMessageChannel.subscribe(theSubscriber);

        theFluxMessageChannel.send(theInputMessage);
        // </editor-fold>
        await().atMost(2, TimeUnit.SECONDS).until(() ->
            theSubscriber.getSubscriberReceivedMessages().size() > 0);

        Assert.assertTrue("A single message should have been received",
            theSubscriber.getSubscriberReceivedMessages().size() == 1);

        verifyDirectMessageChannelInterception(theLoggingAndCountingChannelInterceptor);
    }

    /**
     * Tests creating a priority message channel with a subscriber and an executor channel
     * interceptor registered on the channel. A message is then sent to the channel.
     *
     * Expected result: Sending message to the channel should be intercepted.
     * Receiving message from the channel should not be intercepted.
     * Message handling should not be intercepted.
     *
     * @throws Exception If an error occurs. Indicates test failure.
     */
    @Test
    public void priorityChannelWithExecutorChannelInterceptorTest() throws Exception {
        final PriorityChannel thePriorityChannel;
        final Message<String> theInputMessage;
        final Message<?> theOutputMessage;
        final ExecutorChannelLoggingAndCountingInterceptor theLoggingAndCountingChannelInterceptor;

        theInputMessage = MessageBuilder.withPayload(GREETING_STRING).build();

        // <editor-fold desc="Answer Section" defaultstate="collapsed">
        thePriorityChannel = new PriorityChannel();
        thePriorityChannel.setComponentName(PRIORITY_CHANNEL_NAME);

        theLoggingAndCountingChannelInterceptor =
            new ExecutorChannelLoggingAndCountingInterceptor();
        thePriorityChannel.addInterceptor(theLoggingAndCountingChannelInterceptor);

        thePriorityChannel.send(theInputMessage);
        // </editor-fold>
        theOutputMessage =
            thePriorityChannel.receive(RECEIVE_TIMEOUT_5000_MILLISECONDS);

        Assert.assertNotNull("A message should have been received", theOutputMessage);

        /* Sending of message should have been intercepted. */
        Assert.assertEquals(
            "Message sending should have been intercepted before"
                + " the message being sent", 1,
            theLoggingAndCountingChannelInterceptor.getPreSendMessageCount());
        Assert.assertEquals("Message sending should have been intercepted "
                + "after the message having been sent", 1,
            theLoggingAndCountingChannelInterceptor.getPostSendMessageCount());
        Assert.assertEquals("Message sending should have completed", 1,
            theLoggingAndCountingChannelInterceptor.getAfterSendCompletionMessageCount());

        /* Handling of message should not have been intercepted. */
        Assert.assertEquals("Message handling should not have been intercepted "
                + "before message was handled",
            0, theLoggingAndCountingChannelInterceptor.getBeforeHandleMessageCount());
        Assert.assertEquals("Message handling should not have been intercepted "
                + "after message was handled", 0,
            theLoggingAndCountingChannelInterceptor.getAfterMessageHandledMessageCount());

        /* Receiving of message should have been intercepted. */
        Assert.assertEquals(
            "Receiving should be intercepted with priority message channels",
            1, theLoggingAndCountingChannelInterceptor.getPreReceiveMessageCount());
        Assert.assertEquals(
            "Receiving should be intercepted with priority message channels",
            1, theLoggingAndCountingChannelInterceptor.getPostReceiveMessageCount());
        Assert.assertEquals(
            "Receiving should be intercepted with priority message channels",
            1,
            theLoggingAndCountingChannelInterceptor.getAfterReceiveCompletionMessageCount());
    }

    /**
     * Verifies the supplied executor channel interceptor as to have intercepted
     * messages as having been intercepting messages for a direct message channel.
     *
     * @param inLoggingAndCountingChannelInterceptor Interceptor which to verify.
     */
    protected void verifyDirectMessageChannelInterception(final ExecutorChannelLoggingAndCountingInterceptor
        inLoggingAndCountingChannelInterceptor) {
        /* Sending of message should have been intercepted. */
        Assert.assertEquals(
            "Message sending should have been intercepted before"
                + " the message being sent", 1,
            inLoggingAndCountingChannelInterceptor.getPreSendMessageCount());
        Assert.assertEquals("Message sending should have been intercepted "
                + "after the message having been sent", 1,
            inLoggingAndCountingChannelInterceptor.getPostSendMessageCount());
        Assert.assertEquals("Message sending should have completed", 1,
            inLoggingAndCountingChannelInterceptor.getAfterSendCompletionMessageCount());

        /* Handling of message should not have been intercepted. */
        Assert.assertEquals("Message handling should not have been intercepted "
                + "before message was handled",
            0, inLoggingAndCountingChannelInterceptor.getBeforeHandleMessageCount());
        Assert.assertEquals("Message handling should not have been intercepted "
                + "after message was handled", 0,
            inLoggingAndCountingChannelInterceptor.getAfterMessageHandledMessageCount());

        /* Receiving of message should not have been intercepted. */
        Assert.assertEquals(
            "Receiving will not be intercepted by message channels with subscribers",
            0, inLoggingAndCountingChannelInterceptor.getPreReceiveMessageCount());
        Assert.assertEquals(
            "Receiving will not be intercepted by message channels with subscribers",
            0, inLoggingAndCountingChannelInterceptor.getPostReceiveMessageCount());
        Assert.assertEquals(
            "Receiving will not be intercepted by message channels with subscribers",
            0, inLoggingAndCountingChannelInterceptor.getAfterReceiveCompletionMessageCount());
    }
}
