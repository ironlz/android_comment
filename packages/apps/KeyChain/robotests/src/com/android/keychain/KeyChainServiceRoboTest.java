/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keychain;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyVararg;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.admin.SecurityLog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.security.IKeyChainService;

import com.android.org.conscrypt.TrustedCertificateStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION,
        shadows = {
                ShadowTrustedCertificateStore.class,
                ShadowPackageManager.class,
        })
public final class KeyChainServiceRoboTest {
    private IKeyChainService.Stub mKeyChain;

    @Mock
    private KeyChainService.Injector mockInjector;
    @Mock
    private TrustedCertificateStore mockCertStore;

    /*
     * The CA cert below is the content of cacert.pem as generated by:
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     */
    private static final String TEST_CA =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDXTCCAkWgAwIBAgIJAK9Tl/F9V8kSMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQwHhcNMTUwMzA2MTczMjExWhcNMjUwMzAzMTczMjExWjBF\n" +
            "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
            "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
            "CgKCAQEAvItOutsE75WBTgTyNAHt4JXQ3JoseaGqcC3WQij6vhrleWi5KJ0jh1/M\n" +
            "Rpry7Fajtwwb4t8VZa0NuM2h2YALv52w1xivql88zce/HU1y7XzbXhxis9o6SCI+\n" +
            "oVQSbPeXRgBPppFzBEh3ZqYTVhAqw451XhwdA4Aqs3wts7ddjwlUzyMdU44osCUg\n" +
            "kVg7lfPf9sTm5IoHVcfLSCWH5n6Nr9sH3o2ksyTwxuOAvsN11F/a0mmUoPciYPp+\n" +
            "q7DzQzdi7akRG601DZ4YVOwo6UITGvDyuAAdxl5isovUXqe6Jmz2/myTSpAKxGFs\n" +
            "jk9oRoG6WXWB1kni490GIPjJ1OceyQIDAQABo1AwTjAdBgNVHQ4EFgQUH1QIlPKL\n" +
            "p2OQ/AoLOjKvBW4zK3AwHwYDVR0jBBgwFoAUH1QIlPKLp2OQ/AoLOjKvBW4zK3Aw\n" +
            "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAcMi4voMMJHeQLjtq8Oky\n" +
            "Azpyk8moDwgCd4llcGj7izOkIIFqq/lyqKdtykVKUWz2bSHO5cLrtaOCiBWVlaCV\n" +
            "DYAnnVLM8aqaA6hJDIfaGs4zmwz0dY8hVMFCuCBiLWuPfiYtbEmjHGSmpQTG6Qxn\n" +
            "ZJlaK5CZyt5pgh5EdNdvQmDEbKGmu0wpCq9qjZImwdyAul1t/B0DrsWApZMgZpeI\n" +
            "d2od0VBrCICB1K4p+C51D93xyQiva7xQcCne+TAnGNy9+gjQ/MyR8MRpwRLv5ikD\n" +
            "u0anJCN8pXo6IMglfMAsoton1J6o5/ae5uhC6caQU8bNUsCK570gpNfjkzo6rbP0\n" +
            "wQ==\n" +
            "-----END CERTIFICATE-----\n";

    private X509Certificate mCert;
    private String mSubject;
    private ShadowPackageManager mShadowPackageManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ShadowTrustedCertificateStore.sDelegate = mockCertStore;

        mCert = parseCertificate(TEST_CA);
        mSubject = mCert.getSubjectX500Principal().getName(X500Principal.CANONICAL);

        final PackageManager packageManager = RuntimeEnvironment.application.getPackageManager();
        mShadowPackageManager = shadowOf(packageManager);

        final ServiceController<KeyChainService> serviceController =
                Robolectric.buildService(KeyChainService.class).create().bind();
        final KeyChainService service = serviceController.get();
        service.setInjector(mockInjector);
        final Intent intent = new Intent(IKeyChainService.class.getName());
        mKeyChain = (IKeyChainService.Stub) service.onBind(intent);
    }

    @Test
    public void testCaInstallSuccessLogging() throws Exception {
        setUpLoggingAndAccess(true);

        mKeyChain.installCaCertificate(TEST_CA.getBytes());

        verify(mockInjector, times(1)).writeSecurityEvent(
                SecurityLog.TAG_CERT_AUTHORITY_INSTALLED, 1 /* success */, mSubject);
    }

    @Test
    public void testCaInstallFailedLogging() throws Exception {
        setUpLoggingAndAccess(true);

        doThrow(new IOException()).when(mockCertStore).installCertificate(any());

        try {
            mKeyChain.installCaCertificate(TEST_CA.getBytes());
            fail("didn't propagate the exception");
        } catch (IllegalStateException ignored) {
            assertTrue(ignored.getCause() instanceof IOException);
        }

        verify(mockInjector, times(1)).writeSecurityEvent(
                SecurityLog.TAG_CERT_AUTHORITY_INSTALLED, 0 /* failure */, mSubject);
    }

    @Test
    public void testCaRemoveSuccessLogging() throws Exception {
        setUpLoggingAndAccess(true);

        doReturn(mCert).when(mockCertStore).getCertificate("alias");

        mKeyChain.deleteCaCertificate("alias");

        verify(mockInjector, times(1)).writeSecurityEvent(
                SecurityLog.TAG_CERT_AUTHORITY_REMOVED, 1 /* success */, mSubject);
    }

    @Test
    public void testCaRemoveFailedLogging() throws Exception {
        setUpLoggingAndAccess(true);

        doReturn(mCert).when(mockCertStore).getCertificate("alias");
        doThrow(new IOException()).when(mockCertStore).deleteCertificateEntry(any());

        mKeyChain.deleteCaCertificate("alias");

        verify(mockInjector, times(1)).writeSecurityEvent(
                SecurityLog.TAG_CERT_AUTHORITY_REMOVED, 0 /* failure */, mSubject);
    }

    @Test
    public void testNoLoggingWhenDisabled() throws Exception {
        setUpLoggingAndAccess(false);

        doReturn(mCert).when(mockCertStore).getCertificate("alias");

        mKeyChain.installCaCertificate(TEST_CA.getBytes());
        mKeyChain.deleteCaCertificate("alias");

        doThrow(new IOException()).when(mockCertStore).installCertificate(any());
        doThrow(new IOException()).when(mockCertStore).deleteCertificateEntry(any());

        try {
            mKeyChain.installCaCertificate(TEST_CA.getBytes());
            fail("didn't propagate the exception");
        } catch (IllegalStateException ignored) {
            assertTrue(ignored.getCause() instanceof IOException);
        }
        mKeyChain.deleteCaCertificate("alias");

        verify(mockInjector, never()).writeSecurityEvent(anyInt(), anyInt(), anyVararg());
    }

    private X509Certificate parseCertificate(String cert) throws CertificateException {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cert.getBytes()));
    }

    @Test
    public void testBadPackagesNotAllowedToInstallCaCerts() throws Exception {
        setUpCaller(1000666, null);
        try {
            mKeyChain.installCaCertificate(TEST_CA.getBytes());
            fail("didn't throw the exception");
        } catch (SecurityException expected) {}
    }

    @Test
    public void testNonSystemPackagesNotAllowedToInstallCaCerts()  throws Exception {
        setUpCaller(1000666, "xxx.nasty.flashlight");
        try {
            mKeyChain.installCaCertificate(TEST_CA.getBytes());
            fail("didn't throw the exception");
        } catch (SecurityException expected) {}
    }

    private void setUpLoggingAndAccess(boolean loggingEnabled) {
        doReturn(loggingEnabled).when(mockInjector).isSecurityLoggingEnabled();

        // Pretend that the caller is system.
        setUpCaller(1000, "android.uid.system:1000");
    }

    private void setUpCaller(int uid, String packageName) {
        doReturn(uid).when(mockInjector).getCallingUid();
        mShadowPackageManager.setNameForUid(uid, packageName);
    }
}
