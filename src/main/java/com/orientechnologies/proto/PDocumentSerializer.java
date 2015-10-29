package com.orientechnologies.proto;

import com.google.protobuf.ByteString;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.exception.OSerializationException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.ODocumentEntry;
import com.orientechnologies.orient.core.record.impl.ODocumentInternal;
import com.orientechnologies.orient.core.serialization.OMemoryStream;
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
    @SuppressWarnings("unchecked")
    private static ProtoSerializer.Item toItem(Object iObj, OType type){
        ProtoSerializer.Item.Builder oItem = ProtoSerializer.Item.newBuilder();
        if (type == null) {
            return oItem.build(); // null value
        }
        switch (type) {
            case BOOLEAN:
                oItem.setValBool((Boolean) iObj);
                break;
            case INTEGER:
                oItem.setValInt((Integer) iObj);
                break;
            case SHORT:
                oItem.setValShort((Short) iObj);
                break;
            case LONG:
                oItem.setValLong((Long) iObj);
                break;
            case FLOAT:
                oItem.setValFloat((Float) iObj);
                break;
            case DOUBLE:
                oItem.setValDouble((Double) iObj);
                break;
            case STRING:
                oItem.setValString(((String) iObj));
                break;
            case BINARY:
                oItem.setValBytes(ByteString.copyFrom((byte[]) iObj));
                break;
            case BYTE:
                oItem.setValByte(((Byte) iObj));
                break;
            case LINKLIST:
            case EMBEDDEDLIST:
                ProtoSerializer.List.Builder listBuilder = ProtoSerializer.List.newBuilder();
                if (iObj instanceof Object[]) {
                    for (Object o : (Object[]) iObj) {
                        listBuilder.addValues(toItem(o, getValueType(o)));
                    }
                } else {
                    List<Object> iList = (List<Object>) iObj;
                    for (Object o : iList) {
                        listBuilder.addValues(toItem(o, getValueType(o)));
                    }
                }
                oItem.setValList(listBuilder);
                break;
            case LINKSET:
            case EMBEDDEDSET:
                Set<Object> iSet = (Set<Object>)iObj;
                ProtoSerializer.Set.Builder setBuilder = ProtoSerializer.Set.newBuilder();
                for (Object o : iSet) {
                    setBuilder.addValues(toItem(o, getValueType(o)));
                }
                oItem.setValSet(setBuilder);
                break;
            case LINKMAP:
            case EMBEDDEDMAP:
                Map<String,Object> iMap = (Map<String,Object>)iObj;
                ProtoSerializer.Map.Builder mapBuilder = ProtoSerializer.Map.newBuilder();
                Map<String, ProtoSerializer.Item> oMap = mapBuilder.getMutableValues();
                for (Map.Entry<String, Object> kv : iMap.entrySet()) {
                    oMap.put(kv.getKey(), toItem(kv.getValue(), getValueType(kv.getValue())));
                }
                oItem.setValMap(mapBuilder);
                break;
            case LINK:
                ORID rid = ((OIdentifiable)iObj).getIdentity();
                ProtoSerializer.RID.Builder ridBuilder = ProtoSerializer.RID.newBuilder();
                ridBuilder.setClusterId(rid.getClusterId());
                ridBuilder.setClusterPos(rid.getClusterPosition());
                oItem.setValRid(ridBuilder);
                break;
            case EMBEDDED:
                oItem.setValDocument(toPB((ODocument) iObj));
                break;
            case DATE:
                long dateValue;
                if (iObj instanceof Long) {
                    dateValue = (Long)iObj;
                } else {
                    dateValue = ((Date) iObj).getTime();
                }
                int offset = ODateHelper.getDatabaseTimeZone().getOffset(dateValue);
                oItem.setValDateTime(ProtoSerializer.DateTime.newBuilder().setValue((dateValue + offset) / MILLISEC_PER_DAY));
                break;
            case DATETIME:
                long dtValue;
                if (iObj instanceof Long) {
                    dtValue = (Long)iObj;
                } else {
                    dtValue = ((Date) iObj).getTime();
                }
                oItem.setValDateTime(ProtoSerializer.DateTime.newBuilder().setValue(dtValue));
                break;
            case DECIMAL:
                BigDecimal dec = (BigDecimal)iObj;
                oItem.setValDecimal(ProtoSerializer.Decimal.newBuilder().setScale(dec.scale())
                        .setValue(ByteString.copyFrom(dec.unscaledValue().toByteArray())));
                break;
            default:
                throw new OSerializationException(
                        "Impossible serialize value of type " + iObj + " with the ODocument binary serializer");
        }
        return oItem.build();
    }
    private static ProtoSerializer.Document.Builder toPB(ODocument document) {
        final OClass clazz = document.getSchemaClass();
        final Map<String, OProperty> props = clazz != null ? clazz.propertiesMap() : null;

        final Set<Map.Entry<String, ODocumentEntry>> fields = ODocumentInternal.rawEntries(document);

        ProtoSerializer.Document.Builder builder = ProtoSerializer.Document.newBuilder();
        if (clazz != null) {
            builder.setClass_(clazz.getName());
        }
        Map<String,ProtoSerializer.Item> oFields = builder.getMutableFields();
        for (Map.Entry<String, ODocumentEntry> entry : fields) {
            ODocumentEntry docEntry = entry.getValue();
            if (!docEntry.exist())
                continue;

            if (docEntry.property == null && props != null)
                docEntry.property = props.get(entry.getKey());

            final OType type = getFieldType(docEntry);

            if (type == null && docEntry.value != null) {
                throw new OSerializationException(
                        "Impossible serialize value of type " + docEntry.value + " with the ODocument binary serializer");
            }

            oFields.put(entry.getKey(), toItem(docEntry.value, type));
        }
        return builder;
    }
    public static byte[] serialize(ODocument iDoc){
        return toPB(iDoc).build().toByteArray();
    }
    private static OType getValueType(Object o) {
        if (o instanceof ODocument) {
            return OType.EMBEDDED;
        }
        return OType.getTypeByValue(o);
    }
    private static OType getFieldType(final ODocumentEntry entry) {
        OType type = entry.type;
        if (type == null) {
            final OProperty prop = entry.property;
            if (prop != null)
                type = prop.getType();

        }
        if (type == null || OType.ANY == type)
            type = OType.getTypeByValue(entry.value);
        return type;
    }
    public void serialize(final ODocument document, final OMemoryStream bytes) {
        bytes.setAsFixed(toPB(document).build().toByteArray());
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
        throw new OSerializationException(
                "Unknown deserialize value of type " + item.getValueCase() + " with the ODocument binary serializer");
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
