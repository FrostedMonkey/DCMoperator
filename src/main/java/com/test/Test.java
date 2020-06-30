package com.test;

import com.operation.MyGetScu;
import com.operation.MyMoveScu;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.StandardElementDictionary;
import org.dcm4che3.data.Tag;

import java.io.File;
import java.util.ArrayList;

/**
 * @author : chenchen
 * @ClassName Test
 * @date : 2020-06-30 17:36
 * @Description TODO
 **/
public class Test {
    public static void main(String[] args) {
        Attributes attributes1=new Attributes();
        ElementDictionary a= StandardElementDictionary.INSTANCE;
        attributes1.setString(Tag.SeriesInstanceUID,a.vrOf(Tag.SeriesInstanceUID),"1.2.276.0.7230010.3.1.4.0.8032.154141725.516");
        attributes1.setString(Tag.QueryRetrieveLevel,a.vrOf(org.dcm4che3.data.Tag.QueryRetrieveLevel),"SERIES");
        Attributes attributes2=new Attributes();
        attributes2.setString(Tag.SeriesInstanceUID,a.vrOf(Tag.SeriesInstanceUID),"1.2.276.0.7230010.3.1.4.0.8032.154141364.522");
        attributes2.setString(Tag.QueryRetrieveLevel,a.vrOf(org.dcm4che3.data.Tag.QueryRetrieveLevel),"SERIES");
        Attributes attributes3=new Attributes();
        attributes3.setString(Tag.SeriesInstanceUID,a.vrOf(Tag.SeriesInstanceUID),"1.2.276.0.7230010.3.1.4.0.8032.154144903.530");
        attributes3.setString(Tag.QueryRetrieveLevel,a.vrOf(org.dcm4che3.data.Tag.QueryRetrieveLevel),"SERIES");
        ArrayList<Attributes> attributes=new ArrayList<>();
        attributes.add(attributes1);
        attributes.add(attributes2);
        attributes.add(attributes3);
        ArrayList<File> arrayList = new ArrayList<>();

        for(Attributes as:attributes){
            MyGetScu myGetScu = new MyGetScu();
            myGetScu.initGetScuInfo("DICOM://DCM4CHEE:chenchen@127.0.0.1:11112",MyGetScu.InformationModel.StudyRoot.name(), attributes3,"c:/a");
            myGetScu.getQuery();
            ArrayList<File> fileList = myGetScu.getFileList();
            arrayList.addAll(fileList);
        }

    }
}
