package datasafe;

import com.google.common.io.ByteStreams;
import de.adorsys.datasafe.business.impl.service.DaggerDefaultDatasafeServices;
import de.adorsys.datasafe.business.impl.service.DefaultDatasafeServices;
import de.adorsys.datasafe.directory.api.config.DFSConfig;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileRegistrationService;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileRemovalService;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileRetrievalService;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileUpdatingService;
import de.adorsys.datasafe.directory.impl.profile.config.DefaultDFSConfig;
import de.adorsys.datasafe.encrypiton.api.types.UserID;
import de.adorsys.datasafe.encrypiton.api.types.UserIDAuth;
import de.adorsys.datasafe.privatestore.api.actions.ListPrivate;
import de.adorsys.datasafe.privatestore.api.actions.ReadFromPrivate;
import de.adorsys.datasafe.privatestore.api.actions.RemoveFromPrivate;
import de.adorsys.datasafe.privatestore.api.actions.WriteToPrivate;
import de.adorsys.datasafe.storage.api.StorageService;
import de.adorsys.datasafe.storage.impl.fs.FileSystemStorageService;
import de.adorsys.datasafe.types.api.actions.ListRequest;
import de.adorsys.datasafe.types.api.actions.ReadRequest;
import de.adorsys.datasafe.types.api.actions.WriteRequest;
import de.adorsys.datasafe.types.api.resource.AbsoluteLocation;
import de.adorsys.datasafe.types.api.resource.PrivateResource;
import de.adorsys.datasafe.types.api.resource.ResolvedResource;
import de.adorsys.datasafe.types.api.resource.Uri;
import de.adorsys.datasafe.types.api.types.ReadKeyPassword;
import de.adorsys.datasafe.types.api.types.ReadStorePassword;
import de.adorsys.datasafe.types.api.utils.Obfuscate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class Datasafe {
    public static final String MESSAGE_ONE = "Hello here 1";
    public static final String FOLDER = "folder1";
    public static final String PRIVATE_FILE = "secret.txt";
    public static final String PRIVATE_FILE_PATH = FOLDER + "/" + PRIVATE_FILE;

    protected DFSConfig dfsConfig;
    protected ListPrivate listPrivate;
    protected ReadFromPrivate readFromPrivate;
    protected WriteToPrivate writeToPrivate;
    protected RemoveFromPrivate removeFromPrivate;
    protected ProfileRegistrationService profileRegistrationService;
    protected ProfileUpdatingService profileUpdatingService;
    protected ProfileRemovalService profileRemovalService;
    protected ProfileRetrievalService profileRetrievalService;

    protected UserIDAuth jane;

    protected void initialize(DFSConfig dfsConfig, DefaultDatasafeServices datasafeServices) {
        this.dfsConfig = dfsConfig;
        this.listPrivate = datasafeServices.privateService();
        this.readFromPrivate = datasafeServices.privateService();
        this.writeToPrivate = datasafeServices.privateService();
        this.removeFromPrivate = datasafeServices.privateService();
        this.profileRegistrationService = datasafeServices.userProfile();
        this.profileRemovalService = datasafeServices.userProfile();
        this.profileRetrievalService = datasafeServices.userProfile();
        this.profileUpdatingService = datasafeServices.userProfile();
    }

    protected static StorageDescriptor fs() {
        String path = "file:///tmp/bucket";
        return new StorageDescriptor(
                "FILESYSTEM",
                () -> new FileSystemStorageService(new Uri(path)),
                new Uri(path),
                null, null, null,
                path
        );
    }

    @Getter
    @ToString(of = "name")
    @AllArgsConstructor
    public static class StorageDescriptor {

        private final String name;
        private final Supplier<StorageService> storageService;
        private final Uri location;
        private final String accessKey;
        private final String secretKey;
        private final String region;
        private final String rootBucket;
    }

    public static final ReadStorePassword STORE_PAZZWORD = new ReadStorePassword("PAZZWORD");

    public static DefaultDatasafeServices defaultDatasafeServices(StorageService storageService, Uri systemRoot) {
        return DaggerDefaultDatasafeServices
                .builder()
                .config(dfsConfig(systemRoot))
                .storage(storageService)
                .build();
    }

    public static DFSConfig dfsConfig(Uri systemRoot) {
        return new DefaultDFSConfig(systemRoot, STORE_PAZZWORD);
    }

    private void init() {
        DefaultDatasafeServices datasafeServices = defaultDatasafeServices(fs().getStorageService().get(), fs().getLocation());
        initialize(dfsConfig(fs().getLocation()), datasafeServices);
    }

    public String runTest() {
        try {
            init();
            jane = registerUser("jane");

            writeDataToPrivate(jane, PRIVATE_FILE_PATH, MESSAGE_ONE);

            AbsoluteLocation<ResolvedResource> privateJane = getFirstFileInPrivate(jane);
            return readPrivateUsingPrivateKey(jane, privateJane.getResource().asPrivate());
        } catch (Exception ex) {
            log.info("Error", ex);
            throw ex;
        }
    }


    protected UserIDAuth registerUser(String userName) {
        return registerUser(userName, new ReadKeyPassword(("secure-password " + userName)::toCharArray));
    }

    protected UserIDAuth registerUser(String userName, ReadKeyPassword readKeyPassword) {
        UserIDAuth auth = new UserIDAuth(new UserID(userName), readKeyPassword);
        profileRegistrationService.registerUsingDefaults(auth);
        log.info("Created user: {}", Obfuscate.secure(userName));
        return auth;
    }

    @SneakyThrows
    protected void writeDataToPrivate(UserIDAuth auth, String path, String data) {
        try (OutputStream stream = writeToPrivate.write(WriteRequest.forDefaultPrivate(auth, path))) {
            stream.write(data.getBytes(UTF_8));
        }
        log.info("File {} of user {} saved to {}", Obfuscate.secure(data), auth, Obfuscate.secure(path, "/"));
    }

    protected AbsoluteLocation<ResolvedResource> getFirstFileInPrivate(UserIDAuth owner) {
        return getAllFilesInPrivate(owner).get(0);
    }

    protected List<AbsoluteLocation<ResolvedResource>> getAllFilesInPrivate(UserIDAuth owner) {
        try (Stream<AbsoluteLocation<ResolvedResource>> ls = listPrivate.list(
                ListRequest.forDefaultPrivate(owner, "./")
        )) {
            List<AbsoluteLocation<ResolvedResource>> files = ls.collect(Collectors.toList());
            log.info("{} has {} in PRIVATE", owner.getUserID(), files);
            return files;
        }
    }

    @SneakyThrows
    protected String readPrivateUsingPrivateKey(UserIDAuth user, PrivateResource location) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (InputStream dataStream = readFromPrivate.read(ReadRequest.forPrivate(user, location))) {
            ByteStreams.copy(dataStream, outputStream);
        }

        String data = new String(outputStream.toByteArray(), UTF_8);
        log.info("{} has {} in PRIVATE", user, Obfuscate.secure(data));
        return data;
    }
}
