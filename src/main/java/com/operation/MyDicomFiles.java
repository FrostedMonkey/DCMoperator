package com.operation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.SafeClose;

import java.io.File;

/**
 * @author : chenchen
 * @date : 2020-05-14 09:38
 **/
public class MyDicomFiles {
    private static boolean firstDir=false;
    public interface Callback {
        boolean dicomFile(File f, Attributes fmi, long dsPos, Attributes ds)
                throws Exception;
    }

    public static void scan(File f, MyDicomFiles.Callback scb) {
        if (f.isDirectory()) {
            firstDir=true;
            for (String s : f.list())
                if(new File(f,s).isDirectory()){
                    return;
                }else{
                    scan(new File(f, s), scb);
                }
            return;
        }

        DicomInputStream in = null;
        try {
            in = new DicomInputStream(f);
            in.setIncludeBulkData(IncludeBulkData.NO);
            Attributes fmi = in.readFileMetaInformation();
            long dsPos = in.getPosition();
            Attributes ds = in.readDataset(-1, Tag.PixelData);
            if (fmi == null || !fmi.containsValue(Tag.TransferSyntaxUID)
                    || !fmi.containsValue(Tag.MediaStorageSOPClassUID)
                    || !fmi.containsValue(Tag.MediaStorageSOPInstanceUID))
                fmi = ds.createFileMetaInformation(in.getTransferSyntax());
            boolean b = scb.dicomFile(f, fmi, dsPos, ds);
        } catch (Exception e) {
            System.out.println();
            System.out.println("Failed to scan file " + f + ": " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            SafeClose.close(in);
        }

    }
}
