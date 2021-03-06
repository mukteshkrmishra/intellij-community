// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.mapNotNull;

public class UnshelvePatchDefaultExecutor extends ApplyPatchDefaultExecutor {
  private static final Logger LOG = Logger.getInstance(UnshelvePatchDefaultExecutor.class);

  @NotNull private final ShelvedChangeList myCurrentShelveChangeList;

  public UnshelvePatchDefaultExecutor(@NotNull Project project,
                                      @NotNull ShelvedChangeList changeList) {
    super(project);
    myCurrentShelveChangeList = changeList;
  }

  @Override
  public void apply(@NotNull List<? extends FilePatch> remaining,
                    @NotNull MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable ThrowableComputable<? extends Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    final CommitContext commitContext = new CommitContext();
    applyAdditionalInfoBefore(myProject, additionalInfo, commitContext);
    final Collection<PatchApplier> appliers = getPatchAppliers(patchGroupsToApply, localList, commitContext);
    new Task.Backgroundable(myProject, VcsBundle.getString("unshelve.changes.progress.title")) {
      ApplyPatchStatus myApplyPatchStatus;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myApplyPatchStatus = PatchApplier.executePatchGroup(appliers, localList);
      }

      @Override
      public void onSuccess() {
        if (myApplyPatchStatus != ApplyPatchStatus.ABORT && myApplyPatchStatus != ApplyPatchStatus.FAILURE) {
          removeAppliedAndSaveRemainedIfNeeded(remaining, appliers, commitContext); // remove only if partly applied or successful
        }
      }
    }.queue();
  }

  @CalledInAwt
  private void removeAppliedAndSaveRemainedIfNeeded(@NotNull List<? extends FilePatch> remaining,
                                                    @NotNull Collection<? extends PatchApplier> appliers,
                                                    @NotNull CommitContext commitContext) {
    ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(myProject);
    if (!shelveChangesManager.isRemoveFilesFromShelf()) return;
    try {
      List<FilePatch> patches = new ArrayList<>(remaining);
      patches.addAll(ContainerUtil.concat(appliers, PatchApplier::getRemainingPatches));
      shelveChangesManager
        .updateListAfterUnshelve(myCurrentShelveChangeList, patches, mapNotNull(patches, patch -> patch instanceof ShelvedBinaryFilePatch
                                                                                                  ? ((ShelvedBinaryFilePatch)patch)
                                                                                                    .getShelvedBinaryFile()
                                                                                                  : null), commitContext);
    }
    catch (Exception e) {
      LOG.error("Couldn't update and store remaining patches", e);
    }
  }
}
