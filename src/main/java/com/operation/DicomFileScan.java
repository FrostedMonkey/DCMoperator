package com.operation;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.util.SafeClose;

import java.io.*;
import java.util.ArrayList;
import java.util.UUID;

/**
 * @author : chenchen
 * @date : 2020-05-14 10:24
 **/
public class DicomFileScan {
    int filesScanned = 0;
    /*
     * @author chenchen
     * @data 2020/5/14
     * @return 
     * @description 扫描文件
     */
    public File scanFiles(String fnames, int types, AAssociateRQ rq)
            throws IOException {
        File tempFile = File.createTempFile(UUID.randomUUID().toString(), null);
        tempFile.deleteOnExit();
        final BufferedWriter fileInfos = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tempFile)));
        try {
            if(types==0&&rq==null){//解析用
                MyDicomFiles.scan(new File(fnames), new MyDicomFiles.Callback() {
                    @Override
                    public boolean dicomFile(File f, Attributes fmi, long dsPos,
                                             Attributes ds) throws IOException {
                        if (!addFile(fileInfos, f, fmi))
                            return false;

                        filesScanned++;
                        return true;
                    }
                });
            }else{
                MyDicomFiles.scan(new File(fnames), (f, fmi, dsPos, ds) -> {
                    if (!addFile(fileInfos, f, dsPos, fmi,rq))
                        return false;

                    filesScanned++;
                    return true;
                });
            }

        } finally {
            fileInfos.close();
            return tempFile;
        }
    }

   /**
    * @author chenchen
    * @data 2020/5/14
    * @return
    * @description 将所有文件名写入临时文件
    */
    public boolean addFile(BufferedWriter fileInfos, File f, Attributes fmi) throws IOException {
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        if (cuid == null || iuid == null)
            return false;
        fileInfos.write(f.getPath());
        fileInfos.newLine();
        return true;
    }
    /**
     * @author chenchen
     * @data 2020/5/14
     * @return
     * @description  将所有文件名写入临时文件
     */
    public boolean addFile(BufferedWriter fileInfos, File f, long endFmi,
                           Attributes fmi, AAssociateRQ rq) throws IOException {
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        String ts = fmi.getString(Tag.TransferSyntaxUID);
        if (cuid == null || iuid == null)
            return false;

        fileInfos.write(iuid);
        fileInfos.write('\t');
        fileInfos.write(cuid);
        fileInfos.write('\t');
        fileInfos.write(ts);
        fileInfos.write('\t');
        fileInfos.write(Long.toString(endFmi));
        fileInfos.write('\t');
        fileInfos.write(f.getPath());
        fileInfos.newLine();

        if (rq.containsPresentationContextFor(cuid, ts))
            return true;

        if (!rq.containsPresentationContextFor(cuid)) {
            if (!ts.equals(UID.ExplicitVRLittleEndian))
                rq.addPresentationContext(new PresentationContext(rq
                        .getNumberOfPresentationContexts() * 2 + 1, cuid,
                        UID.ExplicitVRLittleEndian));
            if (!ts.equals(UID.ImplicitVRLittleEndian))
                rq.addPresentationContext(new PresentationContext(rq
                        .getNumberOfPresentationContexts() * 2 + 1, cuid,
                        UID.ImplicitVRLittleEndian));
        }
        rq.addPresentationContext(new PresentationContext(rq
                .getNumberOfPresentationContexts() * 2 + 1, cuid, ts));
        return true;
    }

    /**
     * 此方法暂时只为解析DCM文件用
     * @param fnames
     * @return
     * @throws IOException
     * @author : chenchen
     */
    public ArrayList<File> getFilesPath(String fnames) throws IOException {
        ArrayList<File> fileArrayList = new ArrayList<>();
        File file = scanFiles(fnames,0,null);
        BufferedReader fileInfos = new BufferedReader(new InputStreamReader(
                new FileInputStream(file)));
        try {
            String line;
            while ((line = fileInfos.readLine()) != null) {
                try {
                    fileArrayList.add(new File(line));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            SafeClose.close(fileInfos);
            return fileArrayList;
        }
    }
}
