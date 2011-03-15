/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import com.intellij.util.indexing.FileBasedIndex;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Rustam Vishnyakov
 */
@State(name = "ExcludedFiles", storages = {@Storage(id = "default", file = "$PROJECT_FILE$")})
public class ProjectFileExclusionManager implements PersistentStateComponent<Element> {

  private final static String FILE_ELEMENT = "file";
  private final static String PATH_ATTR = "path";

  private final Map<String,VirtualFile> myExcludedFiles = new TreeMap<String,VirtualFile>();
  private final Project myProject;

  public ProjectFileExclusionManager(Project project) {
    myProject = project;
  }

  public void addExclusion(VirtualFile file) {
    if (file.isDirectory()) return;
    myExcludedFiles.put(file.getPath(), file);
    FileBasedIndex.getInstance().requestReindexExcluded(file);
    fireRootsChange(myProject);
  }

  public void removeExclusion(VirtualFile file) {
    if (myExcludedFiles.containsValue(file)) {
      myExcludedFiles.remove(file.getPath());
      FileBasedIndex.getInstance().requestReindex(file);
      fireRootsChange(myProject);
    }
  }

  private static void fireRootsChange(final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
      }
    });
  }

  public boolean isExcluded(VirtualFile file) {
    return myExcludedFiles.containsKey(file.getPath());
  }

  public Collection<VirtualFile> getExcludedFiles() {
    return myExcludedFiles.values();
  }

  @Override
  public Element getState() {
    final Element root = new Element("root");
    for (String key : myExcludedFiles.keySet()) {
      VirtualFile vf = myExcludedFiles.get(key);
      final Element vfElement = new Element(FILE_ELEMENT);
      final Attribute filePathAttr = new Attribute(PATH_ATTR, VfsUtil.pathToUrl(vf.getPath()));
      vfElement.setAttribute(filePathAttr);
      root.addContent(vfElement);
    }
    return root;
  }

  @Override
  public void loadState(Element state) {
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    for (Object child : state.getChildren(FILE_ELEMENT)) {
      if (child instanceof Element) {
        final Element fileElement = (Element)child;
        final Attribute filePathAttr = fileElement.getAttribute(PATH_ATTR);
        if (filePathAttr != null) {
          final String filePath = filePathAttr.getValue();
          VirtualFile vf = vfManager.findFileByUrl(filePath);
          if (vf != null) {
            myExcludedFiles.put(vf.getPath(), vf);
          }
        }
      }
    }
  }

  @Nullable
  public static ProjectFileExclusionManager getInstance(Project project) {
    return ServiceManager.getService(project, ProjectFileExclusionManager.class);
  }
}
