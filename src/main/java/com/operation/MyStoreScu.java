package com.operation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author : chenchen
 * @date : 2020-05-13 17:25
 * @decription: storescu
 **/
public class MyStoreScu {
    Device device = null;
    private File file;
    private int priority=Priority.NORMAL;
    private String uidSuffix;
    private Attributes attrs = new Attributes();
    private Connection remote = new Connection();
    private ApplicationEntity ae = new ApplicationEntity("OVIYAM2");
    private AAssociateRQ rq = new AAssociateRQ();
    private Association as;
    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService;
    private final RelatedGeneralSOPClasses relSOPClasses = new RelatedGeneralSOPClasses();

    public MyStoreScu() {
        initDevice();
        rq.addPresentationContext(new PresentationContext(1,
                UID.VerificationSOPClass, UID.ImplicitVRLittleEndian));
    }

    public void initDevice() {
        device = new Device("storescu");
        Connection conn = new Connection();
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        conn.setConnectTimeout(20000);
        /**
         * 当文件数量（测试300文件左右）不多时使用单线程化线程池速度更快
         */
        executorService = Executors.newSingleThreadExecutor();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
//        executorService = Executors.newCachedThreadPool();
//        scheduledExecutorService = Executors.newScheduledThreadPool(500);
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);

        remote.setTlsProtocols(conn.getTlsProtocols());
        remote.setTlsCipherSuites(conn.getTlsCipherSuites());

        ae.addConnection(conn);
    }

    public void initServerInfo(String remoteInfo) {
        DcmUrlData dcmUrlData = new DcmUrlData(remoteInfo,null);
        rq.setCalledAET(dcmUrlData.getCalledAET());
        remote.setHostname(dcmUrlData.getHost());
        remote.setPort(dcmUrlData.getPort());
    }

    public void sendDicomFile(String fileName) throws IOException, InterruptedException, GeneralSecurityException, IncompatibleConnectionException {

        scanFileInit(fileName);
        try {
            open();
            sendFiles(file);
            System.out.println("上传成功");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("上传失败");
        } finally {
            close();
            executorService.shutdown();
            scheduledExecutorService.shutdown();
        }
    }

    public void close() throws IOException, InterruptedException {
        if (as != null) {
            if (as.isReadyForDataTransfer())
                as.release();
            as.waitForSocketClose();
        }
    }

    public void sendFiles(File tmpFile) throws IOException {
        BufferedReader fileInfos = new BufferedReader(new InputStreamReader(
                new FileInputStream(tmpFile)));
        try {
            String line;
            while (as.isReadyForDataTransfer()
                    && (line = fileInfos.readLine()) != null) {
                String[] ss = StringUtils.split(line, '\t');
                try {
                    send(new File(ss[4]), Long.parseLong(ss[3]), ss[1], ss[0],
                            ss[2]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                as.waitForOutstandingRSP();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            SafeClose.close(fileInfos);
        }
    }

    private String selectTransferSyntax(String cuid, String filets) {
        Set<String> tss = as.getTransferSyntaxesFor(cuid);
        if (tss.contains(filets))
            return filets;

        if (tss.contains(UID.ExplicitVRLittleEndian))
            return UID.ExplicitVRLittleEndian;

        return UID.ImplicitVRLittleEndian;
    }

    private MyStoreScu.RSPHandlerFactory rspHandlerFactory = new MyStoreScu.RSPHandlerFactory() {

        @Override
        public DimseRSPHandler createDimseRSPHandler(final File f) {

            return new DimseRSPHandler(as.nextMessageID()) {

                @Override
                public void onDimseRSP(Association as, Attributes cmd,
                                       Attributes data) {
                    super.onDimseRSP(as, cmd, data);
                }
            };
        }
    };


    public void send(final File f, long fmiEndPos, String cuid, String iuid,
                     String filets) throws IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        String ts = selectTransferSyntax(cuid, filets);
        if (attrs.isEmpty() && ts.equals(filets)) {
            FileInputStream in = new FileInputStream(f);
            try {
                in.skip(fmiEndPos);
                InputStreamDataWriter data = new InputStreamDataWriter(in);
                as.cstore(cuid, iuid, priority, data, ts,
                        rspHandlerFactory.createDimseRSPHandler(f));
            } finally {
                SafeClose.close(in);
            }
        } else {
            DicomInputStream in = new DicomInputStream(f);
            try {
                in.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                Attributes data = in.readDataset(-1, -1);
                if (ScuUtils.updateAttributes(data, attrs, uidSuffix))
                    iuid = data.getString(Tag.SOPInstanceUID);
                if (!ts.equals(filets)) {
                    Decompressor.decompress(data, filets);
                }
                as.cstore(cuid, iuid, priority,
                        new DataWriterAdapter(data), ts,
                        rspHandlerFactory.createDimseRSPHandler(f));
            } finally {
                SafeClose.close(in);
            }
        }
    }

    public void scanFileInit(String fnames) throws IOException {
        file = new DicomFileScan().scanFiles(fnames,1,rq);
//        ExecutorService executorService = Executors
//                .newSingleThreadExecutor();
//        ScheduledExecutorService scheduledExecutorService = Executors
//                .newSingleThreadScheduledExecutor();
//        device.setExecutor(executorService);
//        device.setScheduledExecutor(scheduledExecutorService);
    }

    public void open() throws IOException, InterruptedException,
            IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(remote, rq);
    }
    public interface RSPHandlerFactory {
        DimseRSPHandler createDimseRSPHandler(File var1);
    }
}
