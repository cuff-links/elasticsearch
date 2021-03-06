/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.ml.process;

import org.elasticsearch.common.io.PathUtils;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.env.Environment;
import org.elasticsearch.test.ESTestCase;
import org.junit.Assert;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class NativeStorageProviderTests extends ESTestCase {

    public void testTmpStorage() throws IOException {
        Path tmpDir = createTempDir();
        long tmpSize = ByteSizeValue.ofGb(6).getBytes();
        NativeStorageProvider storageProvider = createNativeStorageProvider(tmpDir, tmpSize);

        Assert.assertNotNull(
                storageProvider.tryGetLocalTmpStorage(randomAlphaOfLengthBetween(4, 10), ByteSizeValue.ofBytes(100)));
        Assert.assertNull(storageProvider.tryGetLocalTmpStorage(randomAlphaOfLengthBetween(4, 10),
                ByteSizeValue.ofBytes(1024 * 1024 * 1024 + 1)));

        String id = randomAlphaOfLengthBetween(4, 10);
        Path path = storageProvider.tryGetLocalTmpStorage(id, ByteSizeValue.ofGb(1));
        Assert.assertNotNull(path);

        Assert.assertEquals(tmpDir.resolve("ml-local-data").resolve("tmp").resolve(id).toString(), path.toString());
    }

    public void testTmpStorageCleanup() throws IOException {
        Path tmpDir = createTempDir();
        long tmpSize = ByteSizeValue.ofGb(6).getBytes();
        NativeStorageProvider storageProvider = createNativeStorageProvider(tmpDir, tmpSize);
        String id = randomAlphaOfLengthBetween(4, 10);

        Path path = storageProvider.tryGetLocalTmpStorage(id, ByteSizeValue.ofKb(1));

        Assert.assertTrue(Files.exists(path));
        Path testFile = PathUtils.get(path.toString(), "testFile");
        BufferedWriter writer = Files.newBufferedWriter(testFile, StandardCharsets.UTF_8);
        writer.write("created by NativeStorageProviderTests::testTmpStorageDelete");

        writer.close();
        Assert.assertTrue(Files.exists(testFile));
        Assert.assertTrue(Files.isRegularFile(testFile));

        // the native component should cleanup itself, but assume it has crashed
        storageProvider.cleanupLocalTmpStorage(id);
        Assert.assertFalse(Files.exists(testFile));
        Assert.assertFalse(Files.exists(path));
    }

    public void testTmpStorageCleanupOnStart() throws IOException {
        Path tmpDir = createTempDir();
        long tmpSize = ByteSizeValue.ofGb(6).getBytes();
        NativeStorageProvider storageProvider = createNativeStorageProvider(tmpDir, tmpSize);
        String id = randomAlphaOfLengthBetween(4, 10);

        Path path = storageProvider.tryGetLocalTmpStorage(id, ByteSizeValue.ofKb(1));

        Assert.assertTrue(Files.exists(path));
        Path testFile = PathUtils.get(path.toString(), "testFile");

        BufferedWriter writer = Files.newBufferedWriter(testFile, StandardCharsets.UTF_8);
        writer.write("created by NativeStorageProviderTests::testTmpStorageWipe");

        writer.close();
        Assert.assertTrue(Files.exists(testFile));
        Assert.assertTrue(Files.isRegularFile(testFile));

        // create a new storage provider to test the case of a crashed node
        storageProvider = createNativeStorageProvider(tmpDir, tmpSize);
        storageProvider.cleanupLocalTmpStorageInCaseOfUncleanShutdown();
        Assert.assertFalse(Files.exists(testFile));
        Assert.assertFalse(Files.exists(path));
    }

    private NativeStorageProvider createNativeStorageProvider(Path path, long size) throws IOException {
        Environment environment = mock(Environment.class);

        when(environment.dataFile()).thenReturn(path);
        NativeStorageProvider storageProvider = spy(new NativeStorageProvider(environment, ByteSizeValue.ofGb(5)));

        doAnswer(invocation -> {
            if (path.equals(invocation.getArguments()[0])) {
                return size;
            }
            return 0L;
        }).when(storageProvider).getUsableSpace(any(Path.class));

        return storageProvider;
    }

}
