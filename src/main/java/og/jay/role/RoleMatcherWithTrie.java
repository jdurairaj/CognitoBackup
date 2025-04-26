package og.jay.role;

import java.util.*;

public class RoleMatcherWithTrie {

    enum SubRole {
        ADMIN, DEVELOPER, BILLING
    }

    static class TrieNode {
        Map<String, TrieNode> children = new HashMap<>();
        SubRole subRole = null; // only set at the end (leaf node)

        boolean hasHigherPrivilege() {
            return subRole == SubRole.ADMIN || subRole == SubRole.DEVELOPER;
        }
    }

    static class RoleTrie {
        TrieNode root = new TrieNode();

        public void insert(String roleStr) {
            Role role = Role.parse(roleStr);
            TrieNode node = root;

            node = node.children.computeIfAbsent(role.account, k -> new TrieNode());
            if (role.workspace != null) {
                node = node.children.computeIfAbsent(role.workspace, k -> new TrieNode());
            }
            if (role.workgroup != null) {
                node = node.children.computeIfAbsent(role.workgroup, k -> new TrieNode());
            }

            node.subRole = role.subRole;
        }

        public boolean canIssueToken(String requestedRoleStr) {
            Role reqRole = Role.parse(requestedRoleStr);
            TrieNode node = root;

            // Traverse
            node = node.children.get(reqRole.account);
            if (node == null) return false;

            // Check at account level
            if (node.subRole != null && (node.subRole == SubRole.ADMIN ||
                    (node.subRole == SubRole.DEVELOPER && reqRole.subRole == SubRole.DEVELOPER))) {
                return true;
            }

            if (reqRole.workspace != null) {
                node = node.children.get(reqRole.workspace);
                if (node == null) return false;

                // Check at workspace level
                if (node.subRole != null && (node.subRole == SubRole.ADMIN ||
                        (node.subRole == SubRole.DEVELOPER && reqRole.subRole == SubRole.DEVELOPER))) {
                    return true;
                }

                if (reqRole.workgroup != null) {
                    node = node.children.get(reqRole.workgroup);
                    if (node == null) return false;

                    // Check at workgroup level
                    if (node.subRole != null && (node.subRole == SubRole.ADMIN ||
                            (node.subRole == SubRole.DEVELOPER && reqRole.subRole == SubRole.DEVELOPER))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    static class Role {
        String account;
        String workspace; // can be null
        String workgroup; // can be null
        SubRole subRole;

        public Role(String account, String workspace, String workgroup, SubRole subRole) {
            this.account = account;
            this.workspace = workspace;
            this.workgroup = workgroup;
            this.subRole = subRole;
        }

        public static Role parse(String roleStr) {
            try {
                String[] parts = roleStr.split(":");
                String[] identifiers = parts[1].split("-");
                String account = identifiers[0];
                String workspace = null;
                String workgroup = null;

                if (identifiers.length > 1) {
                    workspace = identifiers[1];
                }
                if (identifiers.length > 2 && !identifiers[2].equals("default")) {
                    workgroup = identifiers[2];
                }

                SubRole subRole = SubRole.valueOf(parts[2].toUpperCase());
                return new Role(account, workspace, workgroup, subRole);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid role string: " + roleStr, e);
            }
        }
    }

    public static void main(String[] args) {
        RoleTrie roleTrie = new RoleTrie();

        List<String> userRoles = Arrays.asList(
                "iam:acc1-workspace1-default:admin",
                "iam:acc1-default:admin"
        );

        for (String role : userRoles) {
            roleTrie.insert(role);
        }

        String requestedRole1 = "iam:acc1-workspace1-workgroup1-default:developer";
        String requestedRole2 = "iam:acc1-workspace2-workgroup2-default:developer";
        String requestedRole3 = "iam:acc1-workspace1-default:billing";

        System.out.println(roleTrie.canIssueToken(requestedRole1)); // true
        System.out.println(roleTrie.canIssueToken(requestedRole2)); // true
        System.out.println(roleTrie.canIssueToken(requestedRole3)); // false
    }
}