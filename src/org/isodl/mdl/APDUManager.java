package org.isodl.mdl;

import javacard.framework.APDU;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class APDUManager {

    private static final short MINIMUM_BUFFER_SIZE = 261;
    private static final short MAXIMUM_BUFFER_SIZE = 0x7FFF;

    private static final short VALUE_OUTGOING_EXPECTED_LENGTH = 0;
    private static final short VALUE_OUTGOING_LENGTH = 1;
    private static final short VALUE_INCOMING_LENGTH = 2;
    private static final short VALUE_CDATA_OFFSET = 3;
    private static final short STATUS_VALUES_SIZE = 4;

    private final byte[] mStatusFlags;
    private static final short FLAG_APDU_OUTGOING = 0;
    private static final short FLAG_APDU_RECEIVED = 1;
    private static final short STATUS_FLAGS_SIZE = 1;

    private byte[] mSendBuffer;
    private byte[] mReceiveBuffer;
    private short[] mStatusValues;

    public APDUManager() {
        mStatusValues = JCSystem.makeTransientShortArray(STATUS_VALUES_SIZE, JCSystem.CLEAR_ON_DESELECT);
        mStatusFlags = JCSystem.makeTransientByteArray(STATUS_FLAGS_SIZE, JCSystem.CLEAR_ON_DESELECT);
    }

    public void reset() {
        // TODO
    }

    public boolean process(APDU apdu) {
        byte[] apduBuffer = apdu.getBuffer();
        if (mSendBuffer == null) {
            mSendBuffer = JCSystem.makeTransientByteArray(MINIMUM_BUFFER_SIZE, JCSystem.CLEAR_ON_DESELECT);
        }
        
        if (mReceiveBuffer == null) {
            mReceiveBuffer = JCSystem.makeTransientByteArray(MINIMUM_BUFFER_SIZE, JCSystem.CLEAR_ON_DESELECT);
        }
        
        mStatusValues[VALUE_OUTGOING_EXPECTED_LENGTH] = 0;
        mStatusValues[VALUE_OUTGOING_LENGTH] = 0;
        mStatusValues[VALUE_INCOMING_LENGTH] = 0;
        mStatusValues[VALUE_CDATA_OFFSET] = 0;

        ICUtil.setBit(mStatusFlags, FLAG_APDU_OUTGOING, false);
        ICUtil.setBit(mStatusFlags, FLAG_APDU_RECEIVED, false);

        // TODO: check if there are other cases where selection is not allowed.
        // If there are, this method returns false

        if (isContactlessInterface()) {
            return false;
        }

        return true;
    }

    public boolean isContactlessInterface() {
        final byte protocol = (byte) (APDU.getProtocol() & APDU.PROTOCOL_MEDIA_MASK);
        if (protocol == APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_A || protocol == APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_B) {
            return true;
        } else {
            return false;
        }
    }

    public byte[] getSendBuffer() {
        return mSendBuffer;
    }

    public byte[] getReceiveBuffer() {
        return mReceiveBuffer;
    }

    public short getOffsetCData() {
        return mStatusValues[VALUE_CDATA_OFFSET];
    }

    public short getReceivingLength() {
        return mStatusValues[VALUE_INCOMING_LENGTH];
    }
    
    public short receiveAll() throws ISOException {
        if (ICUtil.getBit(mStatusFlags, FLAG_APDU_RECEIVED)) {
            return mStatusValues[VALUE_INCOMING_LENGTH];
        } else {
            final APDU apdu = APDU.getCurrentAPDU();
            
            short bytesReceived = apdu.setIncomingAndReceive();
            final short lc = apdu.getIncomingLength();
            final short receiveOffset = apdu.getOffsetCdata();
    
            byte[] receiveBuffer = getReceiveBuffer();
            final byte[] apduBuffer = apdu.getBuffer();

            if (bytesReceived != lc) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            
            Util.arrayCopyNonAtomic(apduBuffer, receiveOffset, receiveBuffer, receiveOffset, bytesReceived);
            
            mStatusValues[VALUE_INCOMING_LENGTH] = lc;
            mStatusValues[VALUE_CDATA_OFFSET] = receiveOffset;
            
            ICUtil.setBit(mStatusFlags, FLAG_APDU_RECEIVED, true);
            
            return lc;
        }
    }

    public short setOutgoing() {
        APDU apdu = APDU.getCurrentAPDU();
        mStatusValues[VALUE_OUTGOING_EXPECTED_LENGTH] = apdu.setOutgoing();
        ICUtil.setBit(mStatusFlags, FLAG_APDU_OUTGOING, true);

        return mStatusValues[VALUE_OUTGOING_EXPECTED_LENGTH];
    }

    public void setOutgoingLength(short outLength) {
        mStatusValues[VALUE_OUTGOING_LENGTH] = outLength;
    }

    public void sendAll() {
        APDU apdu = APDU.getCurrentAPDU();
        if (ICUtil.getBit(mStatusFlags, FLAG_APDU_OUTGOING)) {
            final short outLength = mStatusValues[VALUE_OUTGOING_LENGTH];
            apdu.setOutgoingLength(outLength);

            apdu.sendBytesLong(mSendBuffer, (short) 0, outLength);
        }
    }
}