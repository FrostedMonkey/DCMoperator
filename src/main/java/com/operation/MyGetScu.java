package com.operation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author : chenchen
 * @ClassName MyGetScu
 * @date : 2020-05-15 09:21
 * @Description cget
 **/
public class MyGetScu {
    private ArrayList<File> fileList = new ArrayList<>();
    private final Device device = new Device("getscu");
    private final ApplicationEntity ae;
    private final Connection conn = new Connection();
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private int priority = Priority.NORMAL;
    private InformationModel model;
    private File storageDir;
    private Attributes keys = new Attributes();
    private Association as;
    private int cancelAfter;
    ExecutorService executorService=null;
    ScheduledExecutorService scheduledExecutorService = null;
    public static enum InformationModel {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelGET, "STUDY"),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelGET, "STUDY"),
        PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired, "STUDY"),
        CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveGET, "IMAGE"),
        WithoutBulkData(UID.CompositeInstanceRetrieveWithoutBulkDataGET, null),
        HangingProtocol(UID.HangingProtocolInformationModelGET, null),
        ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelGET, null);

        final String cuid;
        final String level;

        InformationModel(String cuid, String level) {
            this.cuid = cuid;
            this.level = level;
        }
    }

    private static String[] IVR_LE_FIRST = {
            UID.ImplicitVRLittleEndian,
            UID.ExplicitVRLittleEndian,
            UID.ExplicitVRBigEndianRetired
    };


    public MyGetScu() {
        ae = new ApplicationEntity("GETSCU");
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.addConnection(conn);
        device.setDimseRQHandler(createServiceRegistry());
    }

    /**
     * @return
     * @author chenchen
     * @data 2020/5/15
     * @description 设置pacs服务器、查询条件等相关信息
     */
    public void initGetScuInfo(String remoteInfo, String InformationModel, Attributes attributes,String path) {
        initRemoteInfo(remoteInfo);
        config();
        configureServiceClass(InformationModel);
        configureKeys(attributes);
        executorService =
                Executors.newSingleThreadExecutor();
        scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);
        configureStorageDirectory(new File(path));
    }
    private void initRemoteInfo(String remoteInfo){
        DcmUrlData dcmUrlData = new DcmUrlData(remoteInfo,null);
        rq.setCalledAET(dcmUrlData.getCalledAET());
        remote.setHostname(dcmUrlData.getHost());
        remote.setPort(dcmUrlData.getPort());
        remote.setTlsProtocols(conn.getTlsProtocols());
        remote.setTlsCipherSuites(conn.getTlsCipherSuites());
    }
    public void getQuery() {
        try {
            try {
                open();
                retrieve();
            } finally {
                close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("查询失败");
        }
    }


    /**
     * @return
     * @author chenchen
     * @data 2020/5/15
     * @description 配置链接信息
     */
    private void config() {
        conn.setReceivePDULength(16378);
        conn.setSendPDULength(16378);
        conn.setConnectTimeout(20000);
    }

    /**
     * @return
     * @author chenchen
     * @data 2020/5/15
     * @description 打开连接
     */
    private void open() throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(conn, remote, rq);
    }

    private DicomServiceRegistry createServiceRegistry() {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(storageSCP);
        return serviceRegistry;
    }

    private BasicCStoreSCP storageSCP = new BasicCStoreSCP("*") {

        @Override
        protected void store(Association as, PresentationContext pc, Attributes rq,
                             PDVInputStream data, Attributes rsp)
                throws IOException {
            if (storageDir == null)
                return;

            String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
            String cuid = rq.getString(Tag.AffectedSOPClassUID);
            String tsuid = pc.getTransferSyntax();
            File file = new File(storageDir, iuid);
            try {
                storeTo(as, as.createFileMetaInformation(iuid, cuid, tsuid),
                        data, file);
            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }

        }


    };

    private void storeTo(Association as, Attributes fmi,
                         PDVInputStream data, File file) throws IOException {

        file.getParentFile().mkdirs();
        DicomOutputStream out = new DicomOutputStream(file);
        try {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
        } finally {
            fileList.add(file);
            SafeClose.close(out);
        }
    }

    private void retrieve() throws IOException, InterruptedException {
        retrieve(keys);
    }

    private void retrieve(Attributes keys) throws IOException, InterruptedException {
        final DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd,
                                   Attributes data) {
                super.onDimseRSP(as, cmd, data);
            }
        };

        retrieve(keys, rspHandler);
       /* if (cancelAfter > 0) {
            device.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        rspHandler.cancel(as);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            },
                    cancelAfter,
                    TimeUnit.MILLISECONDS);
        }*/
    }

    private void retrieve(Attributes keys, DimseRSPHandler rspHandler) throws IOException, InterruptedException {
        as.cget(model.cuid, priority, keys, null, rspHandler);
    }

    private void configureServiceClass(String level) {
        setInformationModel(informationModelOf(level), IVR_LE_FIRST, false);

        String[] files = new String[]{"resource:store-tax.properties"};

        try {
            if (files != null)
                for (String file : files) {
                    Properties p = ScuUtils.loadProperties(file, null);
                    Set<Entry<Object, Object>> entrySet = p.entrySet();
                    for (Entry<Object, Object> entry : entrySet){
                        configureStorageSOPClass((String) entry.getKey(), (String) entry.getValue());
                    }

                }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("cont find resource:store-tax.properties");
        }

    }

    /**
     * @return
     * @author chenchen
     * @data 2020/5/15
     * @description 设置模型信息
     */
    private void setInformationModel(MyGetScu.InformationModel model, String[] tss,
                                     boolean relational) {
        this.model = model;
        rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
        if (relational)
            rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[]{1}));
        if (model.level != null)
            addLevel(model.level);
    }

    /*
     * @author chenchen
     * @data 2020/5/15
     * @return
     * @description 设置查询级别以及对应的UID
     */
    private static MyGetScu.InformationModel informationModelOf(String Level) {
        try {
            return Level != null
                    ? MyGetScu.InformationModel.valueOf(Level)
                    : MyGetScu.InformationModel.StudyRoot;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RuntimeException("");
        }
    }

    private void configureStorageSOPClass(String cuid, String tsuids0) {
        String[] tsuids1 = StringUtils.split(tsuids0, ';');
        for (String tsuids2 : tsuids1) {
            addOfferedStorageSOPClass(ScuUtils.toUID(cuid), ScuUtils.toUIDs(tsuids2));
        }
    }

    private void addOfferedStorageSOPClass(String cuid, String... tsuids) {
        if (!rq.containsPresentationContextFor(cuid))
            rq.addRoleSelection(new RoleSelection(cuid, false, true));
        rq.addPresentationContext(new PresentationContext(
                2 * rq.getNumberOfPresentationContexts() + 1, cuid, tsuids));
    }

    /*
     * @author chenchen
     * @data 2020/5/15
     * @return
     * @description 设置查询参数
     */
    private void configureKeys(Attributes attributes) {
        keys.addAll(attributes);
    }

    /*
     * @author chenchen
     * @data 2020/5/15
     * @return
     * @description 设置查询级别
     */
    private void addLevel(String s) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
    }

    /*
     * @author chenchen
     * @data 2020/5/15
     * @return
     * @description 文件保存路径
     */
    private void configureStorageDirectory(File storageDir) {
        if (storageDir != null)
            if (storageDir.mkdirs())
                System.out.println("M-WRITE " + storageDir);
        this.storageDir = storageDir;
    }

    /*
     * @author chenchen
     * @data 2020/5/15
     * @return
     * @description 关闭连接
     */
    private void close() throws IOException, InterruptedException {
        if (as != null && as.isReadyForDataTransfer()) {
            as.waitForOutstandingRSP();
            as.release();
        }
    }
    public ArrayList<File> getFileList(){
        return this.fileList;
    }
}
