package com.operation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * @author : chenchen
 * @ClassName MyMoveScu
 * @date : 2020-06-10 17:01
 * @Description movescu
 **/
public class MyMoveScu extends Device {
    public static enum InformationModel {
        PatientRoot(UID.PatientRootQueryRetrieveInformationModelMOVE, "STUDY"),
        StudyRoot(UID.StudyRootQueryRetrieveInformationModelMOVE, "STUDY"),
        PatientStudyOnly(UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired, "STUDY"),
        CompositeInstanceRoot(UID.CompositeInstanceRootRetrieveMOVE, "IMAGE"),
        HangingProtocol(UID.HangingProtocolInformationModelMOVE, null),
        ColorPalette(UID.ColorPaletteQueryRetrieveInformationModelMOVE, null);

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
    private ArrayList<File> fileList = new ArrayList<>();
    private final ApplicationEntity ae = new ApplicationEntity("MOVESCU");
    private final Connection conn = new Connection();
    private final Connection remote = new Connection();
    private final AAssociateRQ rq = new AAssociateRQ();
    private int priority = Priority.NORMAL;
    private String destination;
    private InformationModel model;
    private Attributes keys = new Attributes();
    private Association as;
    //private int cancelAfter;
    //private boolean releaseEager;
    private ScheduledFuture<?> scheduledCancel;

    public MyMoveScu(){
        super("movescu");
        addConnection(conn);
        addApplicationEntity(ae);
        ae.addConnection(conn);
    }

    /*
     * @author chenchen
     * @data 2020/6/10
     * @return
     * @description 初始化信息
     */
    public void initGetScuInfo(String remoteInfo,String localInfo, String level, Attributes attributes,String dir) {

        try {
            MyStoreScp myStoreScp=null;
            initRemoteinfo(remoteInfo);
            initLocalinfo(localInfo);
            config();
            configureServiceClass(level);
            configureKeys(attributes);

            ExecutorService executorService =
                    Executors.newSingleThreadExecutor();
            ScheduledExecutorService scheduledExecutorService =
                    Executors.newSingleThreadScheduledExecutor();
            setExecutor(executorService);
            setScheduledExecutor(scheduledExecutorService);
            try {
                myStoreScp= new MyStoreScp(localInfo, dir);
                open();
                retrieve();
            } finally {
                close();
                executorService.shutdown();
                scheduledExecutorService.shutdown();
                fileList=myStoreScp.getFileList();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 初始化pacs信息
     */
    private void initRemoteinfo(String connInfo) {
        DcmUrlData dcmUrlData = new DcmUrlData(connInfo,null);
        rq.setCalledAET(dcmUrlData.getCalledAET());
        remote.setHostname(dcmUrlData.getHost());
        remote.setPort(dcmUrlData.getPort());
        remote.setTlsProtocols(conn.getTlsProtocols());
        remote.setTlsCipherSuites(conn.getTlsCipherSuites());
    }
    /*
     * @author chenchen
     * @data 2020/6/11
     * @return 
     * @description 设置SCP相关信息
     */
    private void initLocalinfo(String localInfo){
        DcmUrlData dcmUrlData = new DcmUrlData(localInfo,"SCP");
        conn.setHostname(dcmUrlData.getHost());
        conn.setPort(dcmUrlData.getPort());
        ae.setAETitle(dcmUrlData.getCalledAET());
        setDestination(dcmUrlData.getCalledAET());
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

    /**
     * @return
     * @author chenchen
     * @data 2020/6/10
     * @description 配置链接信息
     */
    private void config() {
        conn.setReceivePDULength(16378);
        conn.setSendPDULength(16378);
        conn.setConnectTimeout(20000);
    }

    private void configureServiceClass(String level) {
        setInformationModel(informationModelOf(level),
                IVR_LE_FIRST, false);
    }

    private final void setInformationModel(InformationModel model, String[] tss,
                                          boolean relational) {
        this.model = model;
        rq.addPresentationContext(new PresentationContext(1, model.cuid, tss));
        if (relational)
            rq.addExtendedNegotiation(new ExtendedNegotiation(model.cuid, new byte[]{1}));
        if (model.level != null)
            addLevel(model.level);
    }

    private void addLevel(String s) {
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, s);
    }

    /*
     * @author chenchen
     * @data 2020/5/15
     * @return
     * @description 设置查询级别以及对应的UID
     */
    private static MyMoveScu.InformationModel informationModelOf(String Level) {
        try {
            return Level != null
                    ? MyMoveScu.InformationModel.valueOf(Level)
                    : MyMoveScu.InformationModel.StudyRoot;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RuntimeException("");
        }
    }

    private final void setDestination(String destination) {
        this.destination = destination;
    }

    private void open() throws IOException, InterruptedException,
            IncompatibleConnectionException, GeneralSecurityException {
        as = ae.connect(conn, remote, rq);
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

        as.cmove(model.cuid, priority, keys, null, destination, rspHandler);
  /*      if (cancelAfter > 0) {
            scheduledCancel = schedule(new Runnable() {
                                           @Override
                                           public void run() {
                                               try {
                                                   rspHandler.cancel(as);
                                                   if (releaseEager) {
                                                       as.release();
                                                   }
                                               } catch (IOException e) {
                                                   e.printStackTrace();
                                               }
                                           }
                                       },
                    cancelAfter,
                   TimeUnit.MILLISECONDS);
    }*/
    }
    private void close() throws IOException, InterruptedException {
//        if (scheduledCancel != null && releaseEager) { // release by scheduler thread
//            return;
//        }
        if (as != null && as.isReadyForDataTransfer()) {
            //if (!releaseEager) {
                as.waitForOutstandingRSP();
            //}
            as.release();
        }
    }
    public ArrayList<File> getFileList(){
        return this.fileList;
    }
}
