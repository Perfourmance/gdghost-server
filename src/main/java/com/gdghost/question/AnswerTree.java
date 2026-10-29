package com.gdghost.question;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 답변 행 → 트리. depth 3까지 보여 주고, 더 깊은 답글은 depth 3 자리(부모와 같은 줄)에 붙인다 — parentId는 실제 부모.
 * 채택 답변이 맨 위, 나머지와 답글은 오래된 순.
 */
final class AnswerTree {

    static final int MAX_DEPTH = 3;

    private AnswerTree() {
    }

    private static final class Node {
        final QuestionDetailReader.AnswerRow row;
        final int depth;
        final Node holder;
        final List<Node> children = new ArrayList<>();

        Node(QuestionDetailReader.AnswerRow row, int depth, Node holder) {
            this.row = row;
            this.depth = depth;
            this.holder = holder;
        }
    }

    /** rows는 id 오름차순 */
    static List<AnswerNode> build(List<QuestionDetailReader.AnswerRow> rows, Long adoptedAnswerId, long viewerId) {
        Map<Long, Node> byId = new HashMap<>();
        List<Node> roots = new ArrayList<>();
        for (QuestionDetailReader.AnswerRow row : rows) {
            Node parent = row.parentId() == null ? null : byId.get(row.parentId());
            Node node;
            if (parent == null) {
                node = new Node(row, 0, null);
                roots.add(node);
            } else if (parent.depth >= MAX_DEPTH) {
                node = new Node(row, MAX_DEPTH, parent.holder);
                parent.holder.children.add(node);
            } else {
                node = new Node(row, parent.depth + 1, parent);
                parent.children.add(node);
            }
            byId.put(row.id(), node);
        }
        roots.sort(Comparator.comparing((Node node) -> !Long.valueOf(node.row.id()).equals(adoptedAnswerId)));
        return toViews(roots, adoptedAnswerId, viewerId);
    }

    /**
     * 지운 답변은 남은 답글이 있을 때만 자리를 둔다. depth 3 답글은 대상과 같은 리스트(형제)에 있으므로
     * 뒤에서부터 돌며 남긴 답변의 parentId를 모아 함께 본다 — 응답의 parentId가 늘 응답 안을 가리키게.
     */
    private static List<AnswerNode> toViews(List<Node> nodes, Long adoptedAnswerId, long viewerId) {
        List<AnswerNode> views = new ArrayList<>();
        Set<Long> repliedTo = new HashSet<>();
        for (Node node : nodes.reversed()) {
            List<AnswerNode> replies = toViews(node.children, adoptedAnswerId, viewerId);
            QuestionDetailReader.AnswerRow row = node.row;
            AnswerNode view;
            if (row.deleted()) {
                if (replies.isEmpty() && !repliedTo.contains(row.id())) {
                    continue;
                }
                view = new AnswerNode(row.id(), row.parentId(), node.depth, null, null, false, false,
                        row.createdAt(), null, row.deletedBy(), replies);
            } else {
                view = view(row, node.depth, adoptedAnswerId, viewerId, replies);
            }
            views.add(view);
            if (row.parentId() != null) {
                repliedTo.add(row.parentId());
            }
        }
        return views.reversed();
    }

    /** 살아 있는 답변 하나 */
    static AnswerNode view(QuestionDetailReader.AnswerRow row, int depth, Long adoptedAnswerId, long viewerId,
            List<AnswerNode> replies) {
        return new AnswerNode(row.id(), row.parentId(), depth,
                AuthorRef.of(row.author(), row.anonymous(), AnonLabels.answer(row.aliasNo()), viewerId),
                row.body(), Long.valueOf(row.id()).equals(adoptedAnswerId), row.author().id() == viewerId,
                row.createdAt(), row.editedAt(), null, replies);
    }

}
