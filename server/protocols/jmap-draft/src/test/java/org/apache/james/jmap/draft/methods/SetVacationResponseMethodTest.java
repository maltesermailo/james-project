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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.GetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetVacationRequest;
import org.apache.james.jmap.draft.model.SetVacationResponse;
import org.apache.james.jmap.draft.model.VacationResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import reactor.core.publisher.Mono;

public class SetVacationResponseMethodTest {
    private static final String WRONG_ID = "WrongId";
    private static final String TEXT_BODY = "Text body";
    private static final Username USERNAME = Username.fromUsername("username");
    private static final String SUBJECT = "subject";

    private SetVacationResponseMethod testee;
    private VacationRepository vacationRepository;
    private MethodCallId methodCallId;
    private MailboxSession mailboxSession;
    private NotificationRegistry notificationRegistry;

    @Before
    public void setUp() {
        methodCallId = mock(MethodCallId.class);
        mailboxSession = mock(MailboxSession.class);
        vacationRepository = mock(VacationRepository.class);
        notificationRegistry = mock(NotificationRegistry.class);
        testee = new SetVacationResponseMethod(vacationRepository, notificationRegistry, new DefaultMetricFactory());
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullRequest() {
        testee.process(null, mock(MethodCallId.class), mock(MailboxSession.class));
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullMethodCallId() {
        testee.process(mock(SetMailboxesRequest.class), null, mock(MailboxSession.class));
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullMailboxSession() {
        testee.process(mock(SetMailboxesRequest.class), mock(MethodCallId.class), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processShouldThrowOnWrongRequestType() {
        testee.process(mock(GetMailboxesRequest.class), mock(MethodCallId.class), mock(MailboxSession.class));
    }

    @Test
    public void processShouldThrowOnEmptyMap() {
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .update(ImmutableMap.of())
            .build();

        Stream<JmapResponse> result = testee.process(setVacationRequest, methodCallId, mock(MailboxSession.class));

        JmapResponse expected = JmapResponse.builder()
            .methodCallId(methodCallId)
            .error(ErrorResponse.builder()
                .type(SetVacationResponseMethod.INVALID_ARGUMENTS)
                .description(SetVacationResponseMethod.INVALID_ARGUMENT_DESCRIPTION)
                .build())
            .build();
        assertThat(result).containsExactly(expected);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void processShouldThrowIfWrongMapId() {
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .update(ImmutableMap.of(WRONG_ID, VacationResponse.builder()
                .id(Vacation.ID)
                .enabled(false)
                .textBody(Optional.of(TEXT_BODY))
                .build()))
            .build();

        Stream<JmapResponse> result = testee.process(setVacationRequest, methodCallId, mock(MailboxSession.class));

        JmapResponse expected = JmapResponse.builder()
            .methodCallId(methodCallId)
            .error(ErrorResponse.builder()
                .type(SetVacationResponseMethod.INVALID_ARGUMENTS)
                .description(SetVacationResponseMethod.INVALID_ARGUMENT_DESCRIPTION)
                .build())
            .build();
        assertThat(result).containsExactly(expected);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void processShouldThrowIfMapSizeNotOne() {
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .update(ImmutableMap.of(Vacation.ID, VacationResponse.builder()
                    .id(Vacation.ID)
                    .enabled(false)
                    .textBody(Optional.of(TEXT_BODY))
                    .build(),
                WRONG_ID, VacationResponse.builder()
                    .id(Vacation.ID)
                    .enabled(false)
                    .textBody(Optional.of(TEXT_BODY))
                    .build()))
            .build();

        Stream<JmapResponse> result = testee.process(setVacationRequest, methodCallId, mock(MailboxSession.class));

        JmapResponse expected = JmapResponse.builder()
            .methodCallId(methodCallId)
            .error(ErrorResponse.builder()
                .type(SetVacationResponseMethod.INVALID_ARGUMENTS)
                .description(SetVacationResponseMethod.INVALID_ARGUMENT_DESCRIPTION)
                .build())
            .build();
        assertThat(result).containsExactly(expected);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void processShouldUpdateRepositoryUponValidRequest() {
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .update(ImmutableMap.of(Vacation.ID, VacationResponse.builder()
                    .id(Vacation.ID)
                    .enabled(false)
                    .textBody(Optional.of(TEXT_BODY))
                    .subject(Optional.of(SUBJECT))
                    .build()))
            .build();
        AccountId accountId = AccountId.fromString(USERNAME.asString());

        when(mailboxSession.getUser()).thenReturn(USERNAME);
        when(vacationRepository.modifyVacation(eq(accountId), any())).thenReturn(Mono.empty());
        when(notificationRegistry.flush(any()))
            .thenReturn(Mono.empty());

        Stream<JmapResponse> result = testee.process(setVacationRequest, methodCallId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(SetVacationResponseMethod.RESPONSE_NAME)
            .response(SetVacationResponse.builder()
                .updatedId(Vacation.ID)
                .build())
            .build();
        assertThat(result).containsExactly(expected);

        verify(vacationRepository).modifyVacation(eq(accountId), any());
        verify(notificationRegistry).flush(accountId);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void processShouldReturnErrorIfWrongIdIsUsedInsideVacationResponse() {
        SetVacationRequest setVacationRequest = SetVacationRequest.builder()
            .update(ImmutableMap.of(Vacation.ID, VacationResponse.builder()
                .id(WRONG_ID)
                .textBody(Optional.of(TEXT_BODY))
                .enabled(false)
                .build()))
            .build();
        when(mailboxSession.getUser()).thenReturn(USERNAME);

        Stream<JmapResponse> result = testee.process(setVacationRequest, methodCallId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(SetVacationResponseMethod.RESPONSE_NAME)
            .response(SetVacationResponse.builder()
                .notUpdated(Vacation.ID, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description(SetVacationResponseMethod.ERROR_MESSAGE_BASE + WRONG_ID)
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

}
