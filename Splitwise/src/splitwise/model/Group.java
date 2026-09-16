package splitwise.model;

import java.util.LinkedHashSet;
import java.util.Set;

public class Group {

    private final String id;
    private final String name;
    private final Set<String> memberIds = new LinkedHashSet<>();

    public Group(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void addMember(String userId) {
        memberIds.add(userId);
    }

    public boolean removeMember(String userId) {
        return memberIds.remove(userId);
    }

    public boolean hasMember(String userId) {
        return memberIds.contains(userId);
    }

    public Set<String> getMemberIds() {
        return Set.copyOf(memberIds);
    }

    @Override
    public String toString() {
        return name + memberIds;
    }
}
