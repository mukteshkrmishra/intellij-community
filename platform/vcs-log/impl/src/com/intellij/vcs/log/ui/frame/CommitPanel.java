// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.render.RectanglePainter;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.openapi.vcs.ui.FontUtil.getCommitDetailsFont;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.GO_TO_HASH;
import static com.intellij.vcs.log.ui.frame.CommitPresentationUtil.SHOW_HIDE_BRANCHES;

public class CommitPanel extends JBPanel {
  public static final int BOTTOM_BORDER = 2;
  private static final int REFERENCES_BORDER = 12;

  @NotNull private final VcsLogData myLogData;

  @NotNull private final ReferencesPanel myBranchesPanel;
  @NotNull private final ReferencesPanel myTagsPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final BranchesPanel myContainingBranchesPanel;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final Consumer<CommitId> myNavigate;

  @Nullable private CommitId myCommit;

  public CommitPanel(@NotNull VcsLogData logData, @NotNull VcsLogColorManager colorManager, @NotNull Consumer<CommitId> navigate) {
    myLogData = logData;
    myColorManager = colorManager;
    myNavigate = navigate;

    setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    setOpaque(false);

    myBranchesPanel = new ReferencesPanel();
    myBranchesPanel.setBorder(JBUI.Borders.emptyTop(REFERENCES_BORDER));
    myTagsPanel = new ReferencesPanel();
    myTagsPanel.setBorder(JBUI.Borders.emptyTop(REFERENCES_BORDER));
    myMessagePanel = new MessagePanel();
    myContainingBranchesPanel = new BranchesPanel();

    add(myMessagePanel);
    add(myBranchesPanel);
    add(myTagsPanel);
    add(myContainingBranchesPanel);

    setBorder(getDetailsBorder());
  }

  public void setCommit(@NotNull CommitId commit, @NotNull CommitPresentationUtil.CommitPresentation presentation) {
    if (!commit.equals(myCommit) || presentation.isResolved()) {
      myCommit = commit;
      myMessagePanel.setData(presentation);
    }

    List<String> branches = myLogData.getContainingBranchesGetter().requestContainingBranches(commit.getRoot(), commit.getHash());
    myContainingBranchesPanel.setBranches(branches);

    myMessagePanel.update();
    myContainingBranchesPanel.update();
    revalidate();
  }

  public void setRefs(@NotNull Collection<VcsRef> refs) {
    List<VcsRef> references = sortRefs(refs);
    myBranchesPanel.setReferences(references.stream().filter(ref -> ref.getType().isBranch()).collect(Collectors.toList()));
    myTagsPanel.setReferences(references.stream().filter(ref -> !ref.getType().isBranch()).collect(Collectors.toList()));
  }

  public void update() {
    myMessagePanel.update();
    myBranchesPanel.update();
    myTagsPanel.update();
    myContainingBranchesPanel.update();
  }

  public void updateBranches() {
    if (myCommit != null) {
      myContainingBranchesPanel
        .setBranches(myLogData.getContainingBranchesGetter().getContainingBranchesFromCache(myCommit.getRoot(), myCommit.getHash()));
    }
    else {
      myContainingBranchesPanel.setBranches(null);
    }
    myContainingBranchesPanel.update();
  }

  @NotNull
  private List<VcsRef> sortRefs(@NotNull Collection<VcsRef> refs) {
    VcsRef ref = ContainerUtil.getFirstItem(refs);
    if (ref == null) return ContainerUtil.emptyList();
    return ContainerUtil.sorted(refs, myLogData.getLogProvider(ref.getRoot()).getReferenceManager().getLabelsOrderComparator());
  }

  @NotNull
  public static JBEmptyBorder getDetailsBorder() {
    return JBUI.Borders.empty();
  }

  @Override
  public Color getBackground() {
    return getCommitDetailsBackground();
  }

  public boolean isExpanded() {
    return myContainingBranchesPanel.isExpanded();
  }

  @NotNull
  public static Color getCommitDetailsBackground() {
    return UIUtil.getTableBackground();
  }

  private class MessagePanel extends HtmlPanel {
    @Nullable private CommitPresentationUtil.CommitPresentation myPresentation;

    MessagePanel() {
      setBorder(JBUI.Borders.empty(0, ReferencesPanel.H_GAP, BOTTOM_BORDER, 0));
    }

    @Override
    public void hyperlinkUpdate(@NotNull HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription().startsWith(GO_TO_HASH)) {
        CommitId commitId = notNull(myPresentation).parseTargetCommit(e);
        if (commitId != null) myNavigate.consume(commitId);
      }
      else {
        BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
      }
    }

    void setData(@Nullable CommitPresentationUtil.CommitPresentation presentation) {
      myPresentation = presentation;
    }

    @NotNull
    @Override
    protected String getBody() {
      return myPresentation == null ? "" : myPresentation.getText();
    }

    @Override
    public Color getBackground() {
      return getCommitDetailsBackground();
    }
  }

  private static class BranchesPanel extends HtmlPanel {
    @Nullable private List<String> myBranches;
    private boolean myExpanded = false;

    BranchesPanel() {
      setBorder(JBUI.Borders.empty(REFERENCES_BORDER, ReferencesPanel.H_GAP, BOTTOM_BORDER, 0));
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && SHOW_HIDE_BRANCHES.equals(e.getDescription())) {
        myExpanded = !myExpanded;
        update();
      }
    }

    void setBranches(@Nullable List<String> branches) {
      myBranches = branches;
      myExpanded = false;
    }

    @NotNull
    @Override
    protected String getBody() {
      return CommitPresentationUtil.getBranchesText(myBranches, myExpanded);
    }

    @Override
    public Color getBackground() {
      return getCommitDetailsBackground();
    }

    public boolean isExpanded() {
      return myExpanded;
    }
  }
}
