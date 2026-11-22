package za.co.mamadise.support_ticket_tracker.auth;

import java.util.Set;

import za.co.mamadise.support_ticket_tracker.user.Role;

/**
 * Who is making the current request, as far as authorization needs to know: account id, role and (for staff) the ids
 * of the teams they belong to. Built from the database for every request by {@link CurrentUser}, never from client
 * input.
 */
public record Viewer(Long id, Role role, Set<Long> teamIds) {

    public Viewer {
        teamIds = teamIds == null ? Set.of() : Set.copyOf(teamIds);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isAgent() {
        return role == Role.SUPPORT_AGENT;
    }

    /** ADMIN or SUPPORT_AGENT. */
    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.SUPPORT_AGENT;
    }

    public boolean inTeam(Long teamId) {
        return teamId != null && teamIds.contains(teamId);
    }

    public boolean is(Long accountId) {
        return accountId != null && accountId.equals(id);
    }
}
