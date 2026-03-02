package com.flipkart.drift.api.service.utils;

import com.flipkart.drift.commons.model.enums.NodeType;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Shape;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowGraphNode {
    String name;
    NodeType type;
    Shape shape;
    Color color;
    String nextNode = null;
    List<String> choices = new ArrayList<>();

    public WorkflowGraphNode(String name, NodeType type, String nextNode,List<String> choices) {
        this.name = name;
        this.type = type;
        this.shape = getShape(type);
        this.color = getColor(type);
        this.nextNode = nextNode;
        this.choices = choices;
    }

    Shape getShape(NodeType type) {
        switch (type) {
            case BRANCH:
                return Shape.DIAMOND;
            case PROCESSOR:
                return Shape.RECTANGLE;
            case SUB_WORKFLOW:
                return Shape.DOUBLE_OCTAGON;
            default:
                return Shape.ELLIPSE;
        }
    }

    private Color getColor(NodeType type) {
        switch (type) {
            case BRANCH:
                return Color.TAN1;
            case INSTRUCTION:
                return Color.YELLOW;
            case HTTP:
                return Color.DEEPSKYBLUE;
            case FAILURE:
                return Color.FIREBRICK1;
            case GROOVY:
                return Color.MEDIUMSPRINGGREEN;
            case PROCESSOR:
                return Color.GREY;
            case SUB_WORKFLOW:
                return Color.DARKORANGE;
            default:
                return Color.LIGHTPINK;
        }
    }
}




