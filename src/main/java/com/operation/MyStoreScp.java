package com.operation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.util.SafeClose;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/*
 * @author chenchen
 * @data 2020/6/10
 * @return
 * @description storeScp（类似于一个dicom服务端）
 */
public class MyStoreScp {

    private final Device device = new Device("storescp");
    private final ApplicationEntity ae = new ApplicationEntity("*");
    private final Connection conn = new Connection();
    private String storageDir; //接收到dicom文件的存储目录
    private ArrayList<File> fileList = new ArrayList<>();

    private final BasicCStoreSCP cstoreSCP = new BasicCStoreSCP("*") {
        private static final String PART_EXT = ".part";

        @Override
        protected void store(Association as, PresentationContext pc,
                             Attributes rq, PDVInputStream data, Attributes rsp)
                throws IOException {
            try {
                rsp.setInt(Tag.Status, VR.US, 0);
                if (storageDir == null)
                    return;
                String cuid = rq.getString(Tag.AffectedSOPClassUID);
                String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
                String tsuid = pc.getTransferSyntax();
                File file = new File(storageDir, iuid + PART_EXT);
                try {
                    storeTo(as, as.createFileMetaInformation(iuid, cuid, tsuid),
                            data, file);
                    renameTo(as, file, new File(storageDir, iuid));

                } catch (Exception e) {
                    file.delete();
                    throw new DicomServiceException(Status.ProcessingFailure, e);
                }
            } finally {
                release();
            }
        }

    };

    public MyStoreScp(String localInfo,String dir) {
        device.setDimseRQHandler(createServiceRegistry());
        device.addConnection(conn);
        device.addApplicationEntity(ae);
        ae.setAssociationAcceptor(true);
        ae.addConnection(conn);
        //String[] acceptedAETs = { "STORESCP", "DCM4CHEE" };
        //ae.setAcceptedCallingAETitles(acceptedAETs);
        initStoreScp(localInfo);
        setStorageDir(dir);
    }
    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 设置SCP服务ip端口号及AEtitle
     */
    private void initStoreScp(String localInfo) {
        DcmUrlData dcmUrlData = new DcmUrlData(localInfo,"SCP");
        setIP(dcmUrlData.getHost());
        setPort(dcmUrlData.getPort());
        setAETitle(dcmUrlData.getCalledAET());
        statrService();
    }



    private void setAETitle(String strAETitle) {
        ae.setAETitle(strAETitle);
        ae.addTransferCapability(new TransferCapability(null, "*", TransferCapability.Role.SCP, "*"));
    }

    private void statrService() {
        try {
            ExecutorService executorService = Executors.newCachedThreadPool();
            ScheduledExecutorService scheduledExecutorService =
                    Executors.newSingleThreadScheduledExecutor();
            device.setExecutor(executorService);
            device.setScheduledExecutor(scheduledExecutorService);
            device.bindConnections();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private DicomServiceRegistry createServiceRegistry() {
        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
        serviceRegistry.addDicomService(new BasicCEchoSCP());
        serviceRegistry.addDicomService(cstoreSCP);
        return serviceRegistry;
    }

    private void storeTo(Association as, Attributes fmi,
                         PDVInputStream data, File file) throws IOException {
        file.getParentFile().mkdirs();
        DicomOutputStream out = new DicomOutputStream(file);
        try {
            out.writeFileMetaInformation(fmi);
            data.copyTo(out);
        } finally {
            SafeClose.close(out);
        }
    }

    private void renameTo(Association as, File from, File dest)
            throws IOException {
        if (!dest.getParentFile().mkdirs())
            dest.delete();
        if (!from.renameTo(dest))
            throw new IOException("Failed to rename " + from + " to " + dest);
        fileList.add(dest);
    }

    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 接收完数据之后则不再监听关闭此SCP服务
     */
    public void release()  {
        device.unbindConnections();
    }
    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 设置文件存储位置
     */
    private void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 设置remote 端口号
     */
    public void setPort(int nPort) {
        if (conn == null)
            return;
        conn.setPort(nPort);
    }
    /*
     * @author chenchen
     * @data 2020/6/11
     * @return
     * @description 设置remote ip
     */
    public void setIP(String strIP) {
        if (conn == null)
            return;
        conn.setHostname(strIP);
    }
    public ArrayList<File> getFileList(){
        return this.fileList;
    }
}
