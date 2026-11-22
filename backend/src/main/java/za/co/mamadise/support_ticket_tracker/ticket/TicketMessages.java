package za.co.mamadise.support_ticket_tracker.ticket;

/** Short, safe texts about a ticket for notifications. Only number and title: never description or comment text. */
public final class TicketMessages {

    private static final int MAX_TITLE = 120;

    private TicketMessages() {
    }

    public static String ref(Ticket ticket) {
        String title = ticket.getTitle() == null ? "" : ticket.getTitle();
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE - 3) + "...";
        }
        return "#" + ticket.getId() + " " + title;
    }
}
