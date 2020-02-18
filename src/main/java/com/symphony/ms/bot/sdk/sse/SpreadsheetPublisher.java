package com.symphony.ms.bot.sdk.sse;

import com.symphony.ms.bot.sdk.internal.sse.SsePublisher;
import com.symphony.ms.bot.sdk.internal.sse.SseSubscriber;
import com.symphony.ms.bot.sdk.internal.sse.model.SseEvent;
import com.symphony.ms.bot.sdk.internal.sse.model.SubscriptionEvent;
import com.symphony.ms.bot.sdk.internal.symphony.UsersClient;
import com.symphony.ms.bot.sdk.internal.symphony.exception.SymphonyClientException;
import com.symphony.ms.bot.sdk.internal.symphony.model.SymphonyUser;
import com.symphony.ms.bot.sdk.spreadsheet.service.SpreadsheetPresenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sample code. Simple SsePublisher which waits for spreadsheet update events to send to the
 * clients.
 *
 * @author Gabriel Berberian
 */
public class SpreadsheetPublisher extends SsePublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetPublisher.class);
  private static final String SPREADSHEET_PRESENCE_EVENT = "spreadsheetPresenceEvent";
  private static final long WAIT_INTERVAL = 1000L;

  private final UsersClient usersClient;
  private final AtomicLong eventId;
  private final SpreadsheetPresenceService presenceService;

  public SpreadsheetPublisher(UsersClient usersClient, SpreadsheetPresenceService presenceService) {
    this.usersClient = usersClient;
    this.presenceService = presenceService;
    this.eventId = new AtomicLong(0);
  }

  @Override
  public List<String> getEventTypes() {
    return Stream.of("spreadsheetUpdateEvent", "spreadsheetPresenceEvent")
        .collect(Collectors.toList());
  }

  @Override
  public void handleEvent(SseSubscriber subscriber, SseEvent event) {
    String subscriberStreamId = subscriber.getMetadata().get("streamId");
    String eventStreamId = event.getMetadata().get("streamId");
    if (subscriberStreamId == null || eventStreamId == null || subscriberStreamId.equals(
        eventStreamId)) {
      LOGGER.debug("Sending updates to user {}", subscriber.getUserId());
      subscriber.sendEvent(event);
    }
  }

  @Override
  protected void onSubscriberAdded(SubscriptionEvent subscriberAddedEvent) {
    String streamId = subscriberAddedEvent.getMetadata().get("streamId");
    Long userId = subscriberAddedEvent.getUserId();

    SymphonyUser user = getUserById(userId);

    SseEvent presenceEvent = buildPresenceEvent(streamId, user);

    presenceService.beginSending(eventId, presenceEvent, this, streamId, userId);
  }

  @Override
  protected void onSubscriberRemoved(SubscriptionEvent subscriberRemovedEvent) {
    String streamId = subscriberRemovedEvent.getMetadata().get("streamId");
    Long userId = subscriberRemovedEvent.getUserId();

    presenceService.finishSending(streamId, userId);
  }

  public Long getIdAndIncrement() {
    return eventId.getAndIncrement();
  }

  private SymphonyUser getUserById(long userId) {
    try {
      SymphonyUser user = usersClient.getUserFromId(userId, true);
      return user != null ? user : usersClient.getUserFromId(userId, false);
    } catch (SymphonyClientException e) {
      LOGGER.error("Exception getting user by id {}", userId);
      return null;
    }
  }

  private SseEvent buildPresenceEvent(String streamId, SymphonyUser user) {
    return SseEvent.builder()
        .retry(WAIT_INTERVAL)
        .event(SPREADSHEET_PRESENCE_EVENT)
        .data(new HashMap<String, Object>() {
          {
            put("streamId", streamId);
            put("user", user);
          }
        })
        .metadata(new HashMap<String, String>() {
          {
            put("streamId", streamId);
          }
        }).build();
  }

}