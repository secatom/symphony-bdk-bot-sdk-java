package com.symphony.ms.songwriter.internal.elements;

import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.symphony.ms.songwriter.internal.command.BaseCommandHandler;
import com.symphony.ms.songwriter.internal.command.CommandDispatcher;
import com.symphony.ms.songwriter.internal.command.CommandFilter;
import com.symphony.ms.songwriter.internal.command.model.BotCommand;
import com.symphony.ms.songwriter.internal.event.BaseEventHandler;
import com.symphony.ms.songwriter.internal.event.EventDispatcher;
import com.symphony.ms.songwriter.internal.event.model.SymphonyElementsEvent;
import com.symphony.ms.songwriter.internal.feature.FeatureManager;
import com.symphony.ms.songwriter.internal.message.MessageService;
import com.symphony.ms.songwriter.internal.message.model.SymphonyMessage;
import com.symphony.ms.songwriter.internal.symphony.SymphonyService;

public abstract class ElementsHandler implements
    BaseCommandHandler, BaseEventHandler<SymphonyElementsEvent> {
  private static final Logger LOGGER = LoggerFactory.getLogger(ElementsHandler.class);

  private EventDispatcher eventDispatcher;

  private CommandDispatcher commandDispatcher;

  private CommandFilter commandFilter;

  private MessageService messageService;

  private FeatureManager featureManager;

  private SymphonyService symphonyService;

  public void register() {
    commandDispatcher.register(getCommandName(), this);
    commandFilter.addFilter(getCommandName(), getCommandMatcher());
    eventDispatcher.register(getElementsFormId(), this);
  }

  @Override
  public void onCommand(BotCommand command) {
    LOGGER.debug("Received command to display elements form {}",
        command.getMessage());

    final SymphonyMessage elementsResponse = new SymphonyMessage();
    try {
      displayElements(command, elementsResponse);

      if (elementsResponse.hasContent()) {
        messageService.sendMessage(command.getStreamId(), elementsResponse);
      }

    } catch (Exception e) {
      LOGGER.error("Error processing command {}\n{}", getCommandName(), e);
      if (featureManager.unexpectedErrorResponse() != null) {
        messageService.sendMessage(command.getStreamId(),
            new SymphonyMessage(featureManager.unexpectedErrorResponse()));
      }
    }

  }

  @Override
  public void onEvent(SymphonyElementsEvent event) {
    LOGGER.debug("Received action for elements form: {}", event.getFormId());

    final SymphonyMessage eventResponse = new SymphonyMessage();
    try {
      handleAction(event, eventResponse);

      if (eventResponse.hasContent()
          && featureManager.isCommandFeedbackEnabled()) {
        messageService.sendMessage(event.getStreamId(), eventResponse);
      }

    } catch (Exception e) {
      LOGGER.error("Error processing elements action {}", e);
      if (featureManager.unexpectedErrorResponse() != null) {
        messageService.sendMessage(event.getStreamId(),
            new SymphonyMessage(featureManager.unexpectedErrorResponse()));
      }
    }
  }

  private String getCommandName() {
    return this.getClass().getCanonicalName();
  }

  protected String getBotName() {
    return symphonyService.getBotDisplayName();
  }

  protected abstract Predicate<String> getCommandMatcher();

  protected abstract String getElementsFormId();

  public abstract void displayElements(BotCommand command,
      final SymphonyMessage elementsResponse);

  public abstract void handleAction(SymphonyElementsEvent event,
      final SymphonyMessage elementsResponse);

  public void setEventDispatcher(EventDispatcher eventDispatcher) {
    this.eventDispatcher = eventDispatcher;
  }

  public void setCommandDispatcher(CommandDispatcher commandDispatcher) {
    this.commandDispatcher = commandDispatcher;
  }

  public void setCommandFilter(CommandFilter commandFilter) {
    this.commandFilter = commandFilter;
  }

  public void setMessageService(MessageService messageService) {
    this.messageService = messageService;
  }

  public void setFeatureManager(FeatureManager featureManager) {
    this.featureManager = featureManager;
  }

  public void setSymphonyService(SymphonyService symphonyService) {
    this.symphonyService = symphonyService;
  }

}