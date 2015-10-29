package com.orientechnologies.proto;

import com.google.protobuf.ByteString;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.util.ODateHelper;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by dennwc on 10/28/15.
 */
public class PDocumentSerializer {
    private static final long      MILLISEC_PER_DAY = 86400000;
    private static ProtoSerializer.Item toItem(Object iObj){
        ProtoSerializer.Item.Builder oItem = ProtoSerializer.Item.newBuilder();
        if (iObj instanceof Boolean) {
            oItem.setValBool((Boolean) iObj);
        } else if (iObj instanceof Integer) {
            oItem.setValInt((Integer) iObj);
        } else if (iObj instanceof Short) {
            oItem.setValShort((Short) iObj);
        } else if (iObj instanceof Long) {
            oItem.setValLong((Long) iObj);
        } else if (iObj instanceof Float) {
            oItem.setValFloat((Float) iObj);
        } else if (iObj instanceof Double) {
            oItem.setValDouble((Double) iObj);
        } else if (iObj instanceof String) {
            oItem.setValString(((String) iObj));
        } else if (iObj instanceof Byte) {
            oItem.setValByte(((Byte) iObj));
        } else if (iObj instanceof byte[]) {
            oItem.setValBytes(ByteString.copyFrom((byte[]) iObj));
        } else if (iObj instanceof List) {
            List<Object> iList = (List<Object>)iObj;
            ProtoSerializer.List.Builder builder = ProtoSerializer.List.newBuilder();
            for (Object o : iList) {
                builder.addValues(toItem(o));
            }
            oItem.setValList(builder);
        } else if (iObj instanceof Set) {
            Set<Object> iList = (Set<Object>)iObj;
            ProtoSerializer.Set.Builder builder = ProtoSerializer.Set.newBuilder();
            for (Object o : iList) {
                builder.addValues(toItem(o));
            }
            oItem.setValSet(builder);
        } else if (iObj instanceof Map) {
            Map<String,Object> iMap = (Map<String,Object>)iObj;
            ProtoSerializer.Map.Builder builder = ProtoSerializer.Map.newBuilder();
            Map<String, ProtoSerializer.Item> oMap = builder.getMutableValues();
            for (Map.Entry<String, Object> kv : iMap.entrySet()) {
                oMap.put(kv.getKey(), toItem(kv.getValue()));
            }
            oItem.setValMap(builder);
        } else if (iObj instanceof ORID) {
            ORID rid = (ORID)iObj;
            ProtoSerializer.RID.Builder builder = ProtoSerializer.RID.newBuilder();
            builder.setClusterId(rid.getClusterId());
            builder.setClusterPos(rid.getClusterPosition());
            oItem.setValRid(builder);
        } else if (iObj instanceof ODocument) {
            ODocument iDoc = (ODocument) iObj;
            oItem.setValDocument(toPB(iDoc));
        } else if (iObj instanceof Object[]) {
            ProtoSerializer.List.Builder builder = ProtoSerializer.List.newBuilder();
            for (Object o : (Object[]) iObj) {
                builder.addValues(toItem(o));
            }
            oItem.setValList(builder);
        } else if (iObj instanceof Date) {
            oItem.setValDateTime(ProtoSerializer.DateTime.newBuilder().setValue(((Date) iObj).getTime()));
        } else if (iObj instanceof BigDecimal) {
            BigDecimal dec = (BigDecimal)iObj;
            oItem.setValDecimal(ProtoSerializer.Decimal.newBuilder().setScale(dec.scale())
                    .setValue(ByteString.copyFrom(dec.unscaledValue().toByteArray())));
        } else if (iObj == null) {
            // bypass
        } else {
            // TODO: throw some exception
        }
        return oItem.build();
    }
    private static ProtoSerializer.Document.Builder toPB(ODocument iDoc) {
        ProtoSerializer.Document.Builder builder = ProtoSerializer.Document.newBuilder();
        if (iDoc.getClassName() != null) {
            builder.setClass_(iDoc.getClassName());
        }
        Map<String,ProtoSerializer.Item> props = builder.getMutableFields();
        for (Map.Entry<String, Object> f : iDoc) {
            props.put(f.getKey(), toItem(f.getValue())); // TODO: check field type
        }
        return builder;
    }
    public static byte[] serialize(ODocument iDoc){
        return toPB(iDoc).build().toByteArray();
    }
    private static Object fromItem(ProtoSerializer.Item item) {
        switch (item.getValueCase()) {
            case VALUE_NOT_SET:
                return null;
            case VAL_BOOL:
                return item.getValBool();
            case VAL_INT:
                return item.getValInt();
            case VAL_SHORT:
                return (short)item.getValShort();
            case VAL_LONG:
                return item.getValLong();
            case VAL_FLOAT:
                return item.getValFloat();
            case VAL_DOUBLE:
                return item.getValDouble();
            case VAL_STRING:
                return item.getValString();
            case VAL_BYTES:
                return item.getValBytes().toByteArray();
            case VAL_BYTE:
                return (byte)item.getValByte();
            case VAL_DATE_TIME:
                return new Date(item.getValDateTime().getValue());
            case VAL_DATE:
                long savedTime = item.getValDateTime().getValue() * MILLISEC_PER_DAY;
                int offset = ODateHelper.getDatabaseTimeZone().getOffset(savedTime);
                return new Date(savedTime - offset);
            case VAL_DECIMAL:
                ProtoSerializer.Decimal dec = item.getValDecimal();
                return new BigDecimal(new BigInteger(dec.getValue().toByteArray()), (int)dec.getScale());
            case VAL_LIST:
                ProtoSerializer.List iList = item.getValList();
                List<Object> oList = new ArrayList<Object>();
                for (ProtoSerializer.Item it : iList.getValuesList()) {
                    oList.add(fromItem(it));
                }
                return oList;
            case VAL_SET:
                ProtoSerializer.Set iSet = item.getValSet();
                Set<Object> oSet = new HashSet<Object>();
                for (ProtoSerializer.Item it : iSet.getValuesList()) {
                    oSet.add(fromItem(it));
                }
                return oSet;
            case VAL_MAP:
                ProtoSerializer.Map iMap = item.getValMap();
                Map<String,Object> oMap = new HashMap<String, Object>();
                for (Map.Entry<String,ProtoSerializer.Item> f : iMap.getValues().entrySet()) {
                    oMap.put(f.getKey(), fromItem(f.getValue()));
                }
                return oMap;
            case VAL_RID:
                ProtoSerializer.RID iRid = item.getValRid();
                return new ORecordId((int)iRid.getClusterId(), iRid.getClusterPos());
            case VAL_DOCUMENT:
                ProtoSerializer.Document iDoc = item.getValDocument();
                return fromPB(iDoc);
        }
        return null; // TODO: throw some exception
    }
    private static ODocument fromPB(ProtoSerializer.Document iDoc) {
        ODocument oDoc = new ODocument();//(iDoc.getClass_()); // TODO: why it fails on Link*?
        for (Map.Entry<String, ProtoSerializer.Item> f : iDoc.getFields().entrySet()) {
            oDoc.field(f.getKey(), fromItem(f.getValue()));
        }
        return oDoc;
    }
    public static ODocument deserialize(byte[] iData) {
        try {
            ProtoSerializer.Document iDoc = ProtoSerializer.Document.parseFrom(iData);
            return fromPB(iDoc);
        } catch (Exception e) {
            return null; // TODO: handle exceptions
        }
    }

}
