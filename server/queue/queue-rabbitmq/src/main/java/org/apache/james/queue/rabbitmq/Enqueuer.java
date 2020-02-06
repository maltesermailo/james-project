/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.queue.rabbitmq;

import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.queue.api.MailQueue.ENQUEUED_METRIC_NAME_PREFIX;

import java.time.Clock;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.mailet.Mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;

class Enqueuer {
    private final MailQueueName name;
    private final Sender sender;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final MailReferenceSerializer mailReferenceSerializer;
    private final Metric enqueueMetric;
    private final MailQueueView mailQueueView;
    private final Clock clock;

    Enqueuer(MailQueueName name, ReactorRabbitMQChannelPool reactorRabbitMQChannelPool, Store<MimeMessage, MimeMessagePartsId> mimeMessageStore,
             MailReferenceSerializer serializer, MetricFactory metricFactory,
             MailQueueView mailQueueView, Clock clock) {
        this.name = name;
        this.sender = reactorRabbitMQChannelPool.getSender();
        this.mimeMessageStore = mimeMessageStore;
        this.mailReferenceSerializer = serializer;
        this.mailQueueView = mailQueueView;
        this.clock = clock;
        this.enqueueMetric = metricFactory.generate(ENQUEUED_METRIC_NAME_PREFIX + name.asString());
    }

    void enQueue(Mail mail) throws MailQueue.MailQueueException {
        EnqueueId enqueueId = EnqueueId.generate();
        saveMail(mail)
            .map(partIds -> new MailReference(enqueueId, mail, partIds))
            .flatMap(Throwing.function(this::publishReferenceToRabbit).sneakyThrow())
            .flatMap(mailQueueView::storeMail)
            .thenEmpty(Mono.fromRunnable(enqueueMetric::increment))
            .block();
    }

    private Mono<MimeMessagePartsId> saveMail(Mail mail) throws MailQueue.MailQueueException {
        try {
            return mimeMessageStore.save(mail.getMessage());
        } catch (MessagingException e) {
            throw new MailQueue.MailQueueException("Error while saving blob", e);
        }
    }

    private Mono<EnqueuedItem> publishReferenceToRabbit(MailReference mailReference) throws MailQueue.MailQueueException {
        OutboundMessage data = new OutboundMessage(
            name.toRabbitExchangeName().asString(),
            EMPTY_ROUTING_KEY,
            getMailReferenceBytes(mailReference));
        return sender.send(Mono.just(data))
            .then(Mono.just(
                EnqueuedItem.builder()
                    .enqueueId(mailReference.getEnqueueId())
                    .mailQueueName(name)
                    .mail(mailReference.getMail())
                    .enqueuedTime(clock.instant())
                    .mimeMessagePartsId(mailReference.getPartsId())
                    .build()));
    }

    private byte[] getMailReferenceBytes(MailReference mailReference) throws MailQueue.MailQueueException {
        try {
            MailReferenceDTO mailDTO = MailReferenceDTO.fromMailReference(mailReference);
            return mailReferenceSerializer.write(mailDTO);
        } catch (JsonProcessingException e) {
            throw new MailQueue.MailQueueException("Unable to serialize message", e);
        }
    }
}
