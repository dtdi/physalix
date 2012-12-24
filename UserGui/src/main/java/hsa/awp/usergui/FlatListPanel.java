/*
 * Copyright (c) 2010-2012 Matthias Klass, Johannes Leimer,
 *               Rico Lieback, Sebastian Gabriel, Lothar Gesslein,
 *               Alexander Rampp, Kai Weidner
 *
 * This file is part of the Physalix Enrollment System
 *
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

package hsa.awp.usergui;

import hsa.awp.campaign.model.Campaign;
import hsa.awp.campaign.model.FifoProcedure;
import hsa.awp.campaign.model.Procedure;
import hsa.awp.event.model.Category;
import hsa.awp.event.model.Event;
import hsa.awp.gui.util.LoadableDetachedModel;
import hsa.awp.user.model.SingleUser;
import hsa.awp.usergui.controller.IUserGuiController;
import hsa.awp.usergui.util.JavascriptEventConfirmation;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static hsa.awp.event.util.EventFormattingUtils.formatDetailInformation;
import static hsa.awp.event.util.EventFormattingUtils.formatEventId;

/**
 * Flatlist. This panel displays all events for all Categories in a two level list.
 *
 * @author basti & kai
 */
public class FlatListPanel extends Panel {
  /**
   * generated UID.
   */
  private static final long serialVersionUID = 124468394855348482L;

  /**
   * List of Events which will be displayed in each Category.
   */
  private List<Event> events;

  /**
   * percent of remaining slots in event to show yellow icon.
   */
  private float capacityPercent = 0.2f;

  /**
   * GuiController which feeds the Gui with Data.
   */
  @SpringBean(name = "usergui.controller")
  private transient IUserGuiController controller;

  private Campaign campaign;

  private final SingleUser singleUser;

  /**
   * Constructor for the FlatList panel, which defines all needed components.
   *
   * @param id ID which declares the location in the markup.
   */
  public FlatListPanel(String id, final Campaign campaign) {

    super(id);

    this.campaign = campaign;

    singleUser = controller.getUserById(SecurityContextHolder.getContext().getAuthentication().getName());

    final LoadableDetachedModel<List<Category>> categoriesModel = new LoadableDetachedModel<List<Category>>() {
      /**
       * unique serialization id.
       */
      private static final long serialVersionUID = 1594571791900639307L;

      @Override
      protected List<Category> load() {

        List<Category> categories = new LinkedList<Category>();
        for (Category cat : controller.getAllCategories()) {
          if (getEventList(cat).size() > 0) {
            categories.add(cat);
          }
        }

        Collections.sort(categories, new Comparator<Category>() {
          @Override
          public int compare(Category o1, Category o2) {

            int val = o1.getName().compareTo(o2.getName());

            return val;
          }
        });

        return categories;
      }
    };

    final WebMarkupContainer flatListContainer = new WebMarkupContainer("flatlist.container");
    flatListContainer.setOutputMarkupId(true);

    flatListContainer.add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(30f)));

    add(flatListContainer);

    add(new Label("flatlist.capacity", new Model<Integer>((int) (capacityPercent * 100))));

    flatListContainer.add(new ListView<Category>("categorylist", categoriesModel) {
      /**
       * Generated serialization id.
       */
      private static final long serialVersionUID = 490760462608363776L;

      @Override
      protected void populateItem(ListItem<Category> item) {

        events = getEventList(item.getModelObject());

        item.add(new Label("categoryname", item.getModelObject().getName()));
        item.add(new ListView<Event>("eventlist", events) {
          /**
           * Generated serialization id.
           */
          private static final long serialVersionUID = 497760462608363776L;

          @SuppressWarnings("serial")
          @Override
          protected void populateItem(final ListItem<Event> item) {

            final Event event = item.getModelObject();

            int maxParticipants = event.getMaxParticipants();
            long participantCount = controller.countConfirmedRegistrationsByEventId(event.getId());

            if (participantCount > maxParticipants) {
              participantCount = maxParticipants;
            }

            item.add(new Label("eventNumber", formatEventId(event)));
            item.add(new Label("subjectname", event.getSubject().getName()));
            item.add(new Label("eventdescription", formatDetailInformation(event)));
            item.add(new Label("eventplaces", "(" + participantCount + "/" + maxParticipants + ")"));

            Link<Object> link = new Link<Object>("submited") {
              public void onClick() {

                Procedure procedure = campaign.findCurrentProcedure();

                FifoProcedure fifo;

                if (procedure instanceof FifoProcedure) {
                  fifo = (FifoProcedure) procedure;
                } else {
                  throw new IllegalStateException("Flatlist works only with Fifoprocedure.");
                }

                  String initiator = SecurityContextHolder.getContext().getAuthentication().getName();
                  controller.registerWithFifoProcedure(fifo, event, initiator, initiator, true);
              }
            };

            Image icon = new Image("icon");
            icon.add(new AttributeModifier("src", true, new Model<String>()));

            if (controller.checkSingleUserRegistrationForEvent(singleUser, event)) {
              link.setVisible(false);
              item.add(new AttributeAppender("class", new Model<String>("disabled"), " "));
            }

            if ((maxParticipants - participantCount) <= 0) {
              link.setEnabled(false);
              item.add(new AttributeAppender("class", new Model<String>("disabled"), " "));
              icon.add(new AttributeModifier("src", true, new Model<String>("images/red.png")));
            } else if ((Float.valueOf(maxParticipants - participantCount) / maxParticipants) <= capacityPercent) {
              icon.add(new AttributeModifier("src", true, new Model<String>("images/yellow.png")));
            } else {
              icon.add(new AttributeModifier("src", true, new Model<String>("images/green.png")));
            }

            link.add(icon);
            link.add(new JavascriptEventConfirmation("onclick", "Sind Sie sicher?"));
            item.add(link);

            final ModalWindow detailWindow = new ModalWindow("detailWindow");
            detailWindow.setContent(new EventDetailPanel(detailWindow.getContentId(), event));
            detailWindow.setTitle(new Model<String>("Veranstaltungsdetails"));
            detailWindow.setInitialWidth(350);

            item.add(detailWindow);

            item.add(new AjaxFallbackLink<Object>("infoLink") {
              /**
               * unique serialization id.
               */
              private static final long serialVersionUID = 543607735730300949L;

              @Override
              public void onClick(AjaxRequestTarget target) {

                detailWindow.show(target);
              }
            });
          }
        });
      }
    });

    LoadableDetachedModel<String> dateModel = new LoadableDetachedModel<String>() {
      /**
       * unique serialization id.
       */
      private static final long serialVersionUID = -3714278116173742179L;

      @Override
      protected String load() {

        DateFormat singleFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        return singleFormat.format(Calendar.getInstance().getTime());
      }
    };
    flatListContainer.add(new Label("flatlist.date", dateModel));

    flatListContainer.add(new AjaxFallbackLink<Object>("flatlist.refresh") {
      /**
       * unique serialization id.
       */
      private static final long serialVersionUID = 8370439147861506762L;

      @Override
      public void onClick(AjaxRequestTarget target) {

        categoriesModel.detach();
        target.addComponent(flatListContainer);
      }
    });

    flatListContainer.add(new Label("flatlist.userInfo", new LoadableDetachedModel<String>() {
      @Override
      protected String load() {      //Kampangenname: Phase: Phasenname vom xx.xx.xx. hh:mm bis xx.xx.xx hh:mm
        StringBuilder sb = new StringBuilder();
        sb.append(campaign.getName());
        sb.append(": Phase: ");
        Procedure currentProcedure = campaign.findCurrentProcedure();
        sb.append(currentProcedure.getName());
        sb.append(" vom ");
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        sb.append(df.format(currentProcedure.getStartDate().getTime()));
        sb.append(" bis ");
        sb.append(df.format(currentProcedure.getEndDate().getTime()));
        return sb.toString();
      }
    }));
  }

  /**
   * Generates a List<Event> with all Events, which belong to the given Category cat.
   *
   * @param cat Category whose events should be represented in a list.
   * @return List of Events
   */
  private List<Event> getEventList(Category cat) {

    List<Event> ret = new LinkedList<Event>();

    for (Event event : controller.getEventsByCampaign(campaign)) {
      if (event.getSubject().getCategory().equals(cat) && controller.isRegistrationAllowed(singleUser, campaign, event)) {
        ret.add(event);
      }
    }

    Collections.sort(ret, new Comparator<Event>() {
      @Override
      public int compare(Event o1, Event o2) {

        int val = o1.getSubject().getName().compareTo(o2.getSubject().getName());
        if (val == 0) {
          return ((Integer) o1.getEventId()).compareTo(o2.getEventId());
        }
        return val;
      }
    });

    return ret;
  }

  /**
   * Getter for capacityPercent.
   *
   * @return the capacityPercent
   */
  public float getCapacityPercent() {

    return capacityPercent;
  }

  /**
   * setter for capacityPercent.
   *
   * @param capacityPercent the capacityPercent to set
   */
  public void setCapacityPercent(float capacityPercent) {

    this.capacityPercent = capacityPercent;
  }
}
