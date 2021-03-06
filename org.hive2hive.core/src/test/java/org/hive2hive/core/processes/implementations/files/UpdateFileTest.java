package org.hive2hive.core.processes.implementations.files;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.H2HSession;
import org.hive2hive.core.H2HWaiter;
import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.exceptions.GetFailedException;
import org.hive2hive.core.exceptions.IllegalFileLocation;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.file.FileTestUtil;
import org.hive2hive.core.model.FileIndex;
import org.hive2hive.core.model.Index;
import org.hive2hive.core.model.MetaFileSmall;
import org.hive2hive.core.model.UserProfile;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.NetworkTestUtil;
import org.hive2hive.core.processes.ProcessFactory;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.processes.framework.interfaces.IProcessComponent;
import org.hive2hive.core.processes.util.DenyingMessageReplyHandler;
import org.hive2hive.core.processes.util.TestProcessComponentListener;
import org.hive2hive.core.processes.util.UseCaseTestUtil;
import org.hive2hive.core.security.EncryptionUtil;
import org.hive2hive.core.security.H2HEncryptionUtil;
import org.hive2hive.core.security.UserCredentials;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests uploading a new version of a file.
 * 
 * @author Nico
 * 
 */
public class UpdateFileTest extends H2HJUnitTest {

	private final int networkSize = 5;
	private final static int CHUNK_SIZE = 1024;
	private List<NetworkManager> network;
	private UserCredentials userCredentials;
	private Path uploaderRoot;
	private File file;

	private NetworkManager uploader;
	private NetworkManager downloader;

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = UpdateFileTest.class;
		beforeClass();

	}

	@Before
	public void createProfileUploadBaseFile() throws IOException, IllegalFileLocation, NoSessionException,
			NoPeerConnectionException {
		network = NetworkTestUtil.createNetwork(networkSize);
		NetworkManager registrar = network.get(0);
		uploader = network.get(1);
		downloader = network.get(2);

		userCredentials = NetworkTestUtil.generateRandomCredentials();

		// make the two clients ignore each other
		uploader.getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());
		downloader.getConnection().getPeer().setObjectDataReply(new DenyingMessageReplyHandler());

		// create the roots and the file manager
		File rootUploader = new File(System.getProperty("java.io.tmpdir"), NetworkTestUtil.randomString());
		uploaderRoot = rootUploader.toPath();
		File rootDownloader = new File(System.getProperty("java.io.tmpdir"), NetworkTestUtil.randomString());

		// register a user
		UseCaseTestUtil.register(userCredentials, registrar);
		UseCaseTestUtil.login(userCredentials, uploader, rootUploader);
		UseCaseTestUtil.login(userCredentials, downloader, rootDownloader);

		// create a file
		file = FileTestUtil.createFileRandomContent(3, rootUploader, CHUNK_SIZE);
		UseCaseTestUtil.uploadNewFile(uploader, file);
	}

	@Test
	public void testUploadNewVersion() throws IOException, GetFailedException, NoSessionException,
			NoPeerConnectionException {
		// overwrite the content in the file
		String newContent = NetworkTestUtil.randomString();
		FileUtils.write(file, newContent, false);
		byte[] md5UpdatedFile = EncryptionUtil.generateMD5Hash(file);

		// upload the new version
		UseCaseTestUtil.uploadNewVersion(uploader, file);

		// download the file and check if version is newer
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(downloader, userCredentials);
		Index index = userProfile.getFileByPath(file, uploaderRoot);
		File downloaded = UseCaseTestUtil.downloadFile(downloader, index.getFilePublicKey());

		// new content should be latest one
		Assert.assertEquals(newContent, FileUtils.readFileToString(downloaded));

		// check the md5 hash
		Assert.assertTrue(H2HEncryptionUtil.compareMD5(downloaded, md5UpdatedFile));
	}

	@Test
	public void testUploadSameVersion() throws IllegalFileLocation, GetFailedException, IOException,
			NoSessionException, InvalidProcessStateException, IllegalArgumentException,
			NoPeerConnectionException {
		// upload the same content again
		IProcessComponent process = ProcessFactory.instance().createUpdateFileProcess(file, uploader);
		TestProcessComponentListener listener = new TestProcessComponentListener();
		process.attachListener(listener);
		process.start();

		H2HWaiter waiter = new H2HWaiter(60);
		do {
			waiter.tickASecond();
		} while (!listener.hasFailed());

		// verify if the md5 hash did not change
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(downloader, userCredentials);
		FileIndex fileNode = (FileIndex) userProfile.getFileByPath(file, uploaderRoot);
		Assert.assertTrue(H2HEncryptionUtil.compareMD5(file, fileNode.getMD5()));

		// verify that only one version was created
		MetaFileSmall metaDocument = (MetaFileSmall) UseCaseTestUtil.getMetaFile(downloader,
				fileNode.getFileKeys());
		Assert.assertEquals(1, metaDocument.getVersions().size());
	}

	@Test
	public void testCleanupMaxNumVersions() throws IOException, GetFailedException, NoSessionException,
			IllegalArgumentException, NoPeerConnectionException, InvalidProcessStateException {
		// overwrite config
		IFileConfiguration limitingConfig = new IFileConfiguration() {

			@Override
			public BigInteger getMaxSizeAllVersions() {
				return BigInteger.valueOf(Long.MAX_VALUE);
			}

			@Override
			public int getMaxNumOfVersions() {
				return 1;
			}

			@Override
			public BigInteger getMaxFileSize() {
				return BigInteger.valueOf(Long.MAX_VALUE);
			}

			@Override
			public int getChunkSize() {
				return H2HConstants.DEFAULT_CHUNK_SIZE;
			}
		};

		H2HSession session = uploader.getSession();
		H2HSession newSession = new H2HSession(session.getProfileManager(), session.getKeyManager(),
				session.getDownloadManager(), limitingConfig, session.getRoot());
		uploader.setSession(newSession);

		// update the file
		FileUtils.write(file, "bla", false);
		UseCaseTestUtil.uploadNewVersion(uploader, file);

		// verify that only one version is online
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(downloader, userCredentials);
		Index fileNode = userProfile.getFileByPath(file, uploaderRoot);
		MetaFileSmall metaFileSmall = (MetaFileSmall) UseCaseTestUtil.getMetaFile(downloader,
				fileNode.getFileKeys());
		Assert.assertEquals(1, metaFileSmall.getVersions().size());
	}

	@Test
	public void testCleanupMaxSize() throws IOException, GetFailedException, NoSessionException,
			IllegalArgumentException, NoPeerConnectionException, InvalidProcessStateException {
		// overwrite config and set the currently max limit
		final long fileSize = file.length();
		IFileConfiguration limitingConfig = new IFileConfiguration() {

			@Override
			public BigInteger getMaxSizeAllVersions() {
				return BigInteger.valueOf(fileSize);
			}

			@Override
			public int getMaxNumOfVersions() {
				return Integer.MAX_VALUE;
			}

			@Override
			public BigInteger getMaxFileSize() {
				return BigInteger.valueOf(Long.MAX_VALUE);
			}

			@Override
			public int getChunkSize() {
				return H2HConstants.DEFAULT_CHUNK_SIZE;
			}
		};

		H2HSession session = uploader.getSession();
		H2HSession newSession = new H2HSession(session.getProfileManager(), session.getKeyManager(),
				session.getDownloadManager(), limitingConfig, session.getRoot());
		uploader.setSession(newSession);

		// update the file (append some data)
		FileUtils.write(file, NetworkTestUtil.randomString(), true);

		UseCaseTestUtil.uploadNewVersion(uploader, file);

		// verify that only one version is online
		UserProfile userProfile = UseCaseTestUtil.getUserProfile(downloader, userCredentials);
		Index fileNode = userProfile.getFileByPath(file, uploaderRoot);
		MetaFileSmall metaFileSmall = (MetaFileSmall) UseCaseTestUtil.getMetaFile(downloader,
				fileNode.getFileKeys());
		Assert.assertEquals(1, metaFileSmall.getVersions().size());
	}

	@After
	public void deleteAndShutdown() throws IOException {
		NetworkTestUtil.shutdownNetwork(network);
		FileUtils.deleteDirectory(uploaderRoot.toFile());
	}

	@AfterClass
	public static void endTest() throws IOException {
		afterClass();
	}
}
