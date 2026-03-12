package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.ChildNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "child")
public interface ChildNodeActivity extends INodeActivity<ChildNode> {
}
