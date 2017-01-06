package com.kalessil.phpStorm.yii2inspections.actors.upadateTranslations.registry;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/*
 * This file is part of the Yii2 Inspections package.
 *
 * Author: Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

final public class UsedTranslationsRegistry {
    @Nullable
    private Project project = null;

    @Nullable
    private ConcurrentHashMap<String, ConcurrentHashMap<String, String>> translations = null;

    public UsedTranslationsRegistry(@NotNull Project project) {
        this.project = project;
    }

    public ConcurrentHashMap<String, ConcurrentHashMap<String, String>> populate() {
        PsiDirectory root =
            null == this.project ? null : PsiManager.getInstance(this.project).findDirectory(this.project.getBaseDir());
        if (null == root) {
            return null;
        }

        this.translations       = new ConcurrentHashMap<>();
        int countProcessedFiles = 0;

        ThreadGroup findTranslateWorkers = new ThreadGroup("Find t-methods invocations");
        ProjectFilesFinder files         = new ProjectFilesFinder(project);
        while (files.hasNext()) {
            final PsiFile theFile = (PsiFile) files.next();
            final String filePath = theFile.getVirtualFile().getCanonicalPath();
            if (null == filePath) {
                continue;
            }

            /* we are looking for twig or php files consuming translations */
            final String fileName = theFile.getName();
            final boolean isPhp   = fileName.endsWith(".php") && !filePath.matches(".*/(translations|messages)/([a-zA-z]{2}(_[a-zA-z]{2})?)/[^/]+\\.php$");
            final boolean isHtml  = fileName.endsWith(".html");
            if (!isPhp && !isHtml) {
                continue;
            }

            final Thread runnerThread = new Thread(findTranslateWorkers,
                () -> {
                    if (isPhp) {
                        new ProjectTranslationPhpCallsFinder(theFile).find(this.translations);
                    }

                    new ProjectTranslationTwigCallsFinder(theFile).find(this.translations);
                });
            runnerThread.run();

            ++countProcessedFiles;
        }

        try {
            while (findTranslateWorkers.activeCount() > 0) {
                wait(100);
            }
        } catch (InterruptedException interrupted) {
            final String group   = "Yii2 Inspections";
            final String message = "Used translations scan has been interrupted";
            Notifications.Bus.notify(new Notification(group, group, message, NotificationType.ERROR), this.project);
        }

        /* TODO: remove this debug */
        int countTranslations = 0;
        for (ConcurrentHashMap<String, String> translationGroup : this.translations.values()) {
            countTranslations += translationGroup.size();
        }
        final String group   = "Yii2 Inspections";
        final String message = "Usages: files " + countProcessedFiles + " groups " + this.translations.size() + " messages " + countTranslations;
        Notifications.Bus.notify(new Notification(group, group, message, NotificationType.INFORMATION), this.project);

        return this.translations;
    }
}