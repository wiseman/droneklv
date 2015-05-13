package com.lemondronor.droneklv;

import java.util.*;

//TODO: Create tests for constructors


/**
 * <p>A public domain class for working with Key-Length-Value (KLV)
 * byte-packing and unpacking. Supports 1-, 2-, 4-byte, and BER-encoded
 * length fields and 1-, 2-, 4-, and 16-byte key fields. Provides
 * auto-mapping of KLV elements within a payload to the
 * <code>java.util.Map</code> interface.</p>
 *
 * <p>KLV has been used for years as a repeatable, no-guesswork technique
 * for byte-packing data, that is, sending data in a binary format
 * with two bytes for this integer, four bytes for that float,  and
 * so forth. KLV is used in broadcast television and is defined in
 * SMPTE 336M-2001, but it also greatly eases the burden of non-TV-related
 * applications for an easy, interchangeable binary format.</p>
 *
 * <p>The underlying byte array is always king. If you change the key
 * length ({@link #setKeyLength}) or change the length encoding
 * ({@link #setLengthEncoding}), you only change how the underlying
 * byte array is interpreted on subsequent calls.</p>
 *
 * <p>Everything in KLV is Big Endian.</p>
 *
 * <p>All <tt>getValue...</tt> methods will return up to the number
 * of bytes specified in the length fields ({@link #getDeclaredValueLength}) unless
 * there are fewer bytes actually given than are intended in which
 * case {@link #getActualValueLength} bytes will be used. This is to make
 * the code more robust for reading corrupted data. </p>
 *
 * <p>This code is released into the Public Domain. Enjoy.</p>
 *
 * @author Robert Harder
 * @author rharder # users.sourceforge.net
 * @version 0.3
 */
public class KLV {


/* ********  E N U M  ******** */

    /**
     * The encoding style for the length field can be fixed at
     * one byte, two bytes, four bytes, or variable with
     * Basic Encoding Rules (BER).
     */
    public static enum LengthEncoding {
        OneByte     (1),
        TwoBytes    (2),
        FourBytes   (4),
        BER         (5);    // Max bytes a BER field could take up
        private int value;
        LengthEncoding( int value ){ this.value = value; }

        /**
         * Returns the number of bytes used to encode length,
         * or zero if encoding is <code>BER</code>
         */
        public int value(){ return this.value; }

        /**
         * Returns the LengthEncoding matching <code>value</code>
         * with zero mapping to BER or null if no match.
         * @param value the matching length encoding
         */
        public static LengthEncoding valueOf( int value ){
            switch( value ){
                case 1 : return OneByte;
                case 2 : return TwoBytes;
                case 4 : return FourBytes;
                case 0 : return BER;
                default: return null;
            }   // end switch
        }   // end valueOf

    }   // end enum LengthEncoding





    /**
     * The number of bytes in the key field can be
     * one byte, two bytes, four bytes, or sixteen bytes.
     */
    public static enum KeyLength {
        OneByte     (1),
        TwoBytes    (2),
        FourBytes   (4),
        SixteenBytes(16);

        private int value;
        KeyLength( int value ){ this.value = value; }

        /** Returns the number of bytes used in the key. */
        public int value(){ return this.value; }


        /**
         * Returns the KeyLength matching <code>value</code>
         * or null if no match is found.
         * @param value the matching key length
         */
        public static KeyLength valueOf( int value ){
            switch( value ){
                case  1 : return OneByte;
                case  2 : return TwoBytes;
                case  4 : return FourBytes;
                case 16 : return SixteenBytes;
                default : return null;
            }   // end switch
        }   // end valueOf
    }   // end enum KeyLength


    // These are left over from before I switched to enums
    // although enums require a more modern JVM. -Rob
    /** Indicates length field is one byte. Equal to decimal 1. */
    //public final static int LENGTH_FIELD_ONE_BYTE = 1;

    /** Indicates length field is two bytes.  Equal to decimal 2. */
    //public final static int LENGTH_FIELD_TWO_BYTES = 2;

    /** Indicates length field is four bytes.  Equal to decimal 4. */
    //public final static int LENGTH_FIELD_FOUR_BYTES = 4;

    /** Indicates length field uses basic encoding rules (BER). Equal to decimal 8.  */
    //public final static int LENGTH_FIELD_BER = 8;

    /** Indicates key length of one byte. Equal to decimal 1. */
    //public final static int KEY_LENGTH_ONE_BYTE = 1;

    /** Indicates key length of two bytes.  Equal to decimal 2. */
    //public final static int KEY_LENGTH_TWO_BYTES = 2;

    /** Indicates key length of four bytes.  Equal to decimal 4. */
    //public final static int KEY_LENGTH_FOUR_BYTES = 4;

    /** Indicates key length of 16 bytes.  Equal to decimal 16. */
    //public final static int KEY_LENGTH_SIXTEEN_BYTES = 16;


/* ********  S T A T I C   F I E L D S  ******** */



    /**
     * Default <code>KeyLength</code> value (four bytes) when
     * not otherwise specified.
     */
    public final static KeyLength DEFAULT_KEY_LENGTH = KeyLength.FourBytes;


    /**
     * Default <code>LengthEncoding</code> value (BER) when
     * not otherwise specified.
     */
    public final static LengthEncoding DEFAULT_LENGTH_ENCODING = LengthEncoding.BER;



    /** Default character set encoding to use is UTF-8. */
    public final static String DEFAULT_CHARSET_NAME = "UTF-8";


/* ********  I N S T A N C E   F I E L D S  ******** */



    /**
     * Number of bytes in key.
     */
    private KeyLength keyLength;

    /**
     * The key if the key length is greater than four bytes.
     */
    private byte[] keyIfLong;

    /**
     * The key if the key length is four bytes or fewer.
     */
    private int keyIfShort;


    /**
     * The kind of length encoding used.
     */
    private LengthEncoding lengthEncoding;

    /**
     * The bytes from which the KLV set is made up.
     * May include irrelevant bytes so that byte arrays
     * with offset and length specified separately so arrays
     * can be passed around with a minimum of copying.
     */
    private byte[] value;



    /**
     * When instantiated by reading a byte array, this private
     * field will record the offset of the next byte in the array
     * where perhaps another KLV set begins. This is used by the
     * {@link #bytesToList} method to create a list of KLV sets
     * from a long byte array.
     */
    private int offsetAfterInstantiation;




/* ********  C O N S T R U C T O R S  ******** */



    /**
     * Creates a KLV set with default key length (four bytes),
     * default length encoding (BER), a length of zero, and no value.
     * Other constructors in sub classes are not required to call this constructor.
     */
    public KLV(){
        this.keyLength = DEFAULT_KEY_LENGTH;
        this.lengthEncoding = DEFAULT_LENGTH_ENCODING;
        this.value = new byte[0];
    }





    /**
     * <p>Creates a KLV set from the given byte array, the specified key length,
     * and the specified length field encoding.</p>
     *
     * <p>If there are not as many bytes in the array as the length field
     * suggests, as many bytes as possible will be stored as the value, and
     * the length field will reflect the actual length.</p>
     *
     * @param theBytes                  The bytes that make up the entire KLV set
     * @param keyLength                 The number of bytes in the key.
     * @param lengthEncoding            The length field encoding type.
     * @throws NullPointerException     If any parameters are null.
     * @throws IllegalArgumentException If there are not enough bytes in the array
     *                                  to cover at least the key and the length field.
     */
    public KLV( byte[] theBytes, KeyLength keyLength, LengthEncoding lengthEncoding ){
        this( theBytes, 0, keyLength, lengthEncoding );
    }




    /**
     * <p>Creates a KLV set from the given byte array, the given offset in that array,
     * the total length of the KLV set in the byte array, the specified key length,
     * and the specified length field encoding. </p>
     *
     * <p>If there are not as many bytes in the array as the length field
     * suggests, as many bytes as possible will be stored as the value, and
     * the length field will reflect the actual length.</p>
     *
     * @param theBytes                  The bytes that make up the entire KLV set
     * @param offset                    The offset from beginning of theBytes
     * @param keyLength                 The number of bytes in the key.
     * @param lengthEncoding            The length field encoding type.
     * @throws NullPointerException     If any parameters are null.
     * @throws IllegalArgumentException If there are not enough bytes in the array
     *                                  to cover at least the key and the length field.
     * @throws ArrayIndexOutOfBoundsException   If offset is out of range of the byte array.
     */
    public KLV( byte[] theBytes, int offset, KeyLength keyLength, LengthEncoding lengthEncoding ){

        // Check for null and bad offset
        if( theBytes == null )
            throw new NullPointerException( "KLV byte array must not be null." );
        if( keyLength == null )
            throw new NullPointerException( "Key length must not be null." );
        if( lengthEncoding == null )
            throw new NullPointerException( "Length encoding must not be null." );
        if( offset < 0 || offset >= theBytes.length )
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Offset %d is out of range (byte array length: %d).",
                    offset, theBytes.length ) );

        // These public methods will interpret the byte array
        // and set the appropriate key length and length encoding flags.
        // setLength returns the offset of where the length field ends
        // and the value portion begins. It also initializes an array in
        // this.value of the appropriate length.
        setKey( theBytes, offset, keyLength );

        // Set length and verify enough bytes exist
        // setLength(..) also establishes a this.value array.
        int valueOffset = setLength( theBytes, offset + keyLength.value(), lengthEncoding );
        int remaining = theBytes.length - valueOffset;
        if( remaining < this.value.length )
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Not enough bytes left in array (%d) for the declared length (%d).",
                    remaining, this.value.length ) );


        System.arraycopy(theBytes,valueOffset, this.value,0,this.value.length);

        // Private field used when creating a list of KLVs from a long array.
        this.offsetAfterInstantiation = valueOffset + this.value.length;
    }   // end constructor




    /**
     * Create a KLV set with the given key, key length, length field encoding,
     * and provided value in a byte array. If <tt>value</tt> is <tt>null</tt>,
     * then a zero-length value is assumed.
     */
    public KLV( int shortKey, KeyLength keyLength, LengthEncoding lengthFieldEncoding, byte[] value ){
        this( shortKey, keyLength, lengthFieldEncoding, value, 0, value.length );
    }


    /**
     * Create a KLV set with the given key, key length, length field encoding,
     * and provided value in a byte array. If <tt>value</tt> is <tt>null</tt>,
     * then a zero-length value is assumed.
     */
    public KLV( int shortKey, KeyLength keyLength, LengthEncoding lengthEncoding, byte[] value, int offset, int length ){

        // Check for bad parameters
        if( keyLength == null )
            throw new NullPointerException( "Key length must not be null." );
        if( lengthEncoding == null )
            throw new NullPointerException( "Length encoding must not be null." );
        if( value != null ){
            if( offset < 0 )
                throw new ArrayIndexOutOfBoundsException( "Offset must not be negative: " + offset );
            if( value.length > 0 && offset >= value.length )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Offset %d is out of range (byte array length: %d).",
                        offset, value.length ) );
            if( length - offset < value.length )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Not enough bytes in array (%d) for declared length (%d).",
                        value.length, length ) );
        }   // end if: value not null


        // Key
        this.keyLength = keyLength;
        this.keyIfShort = shortKey;

        // Length & value
        this.lengthEncoding = lengthEncoding;
        if( value == null ){
            this.value = new byte[0];
        } else {
            switch( lengthEncoding ){
            case OneByte:
                if( length > (1<<8)-1 )
                    throw new IllegalArgumentException(String.format(
                            "%s encoding cannot support a %d-byte value.",
                            lengthEncoding, length ) );
                this.value = new byte[length];
                System.arraycopy(value,offset, this.value,0,length);
                break;

            case TwoBytes:
                if( length > (1<<16)-1 )
                    throw new IllegalArgumentException(String.format(
                            "%s encoding cannot support a %d-byte value.",
                            lengthEncoding, length ) );
                this.value = new byte[length];
                System.arraycopy(value,offset, this.value,0,length);
                break;

            case FourBytes:
            case BER:
                // Any Java length is allowed.
                this.value = new byte[ length ];
                System.arraycopy(value,offset, this.value,0,length);
                break;

            default:
                assert false : lengthEncoding; // We've accounted for all types
            }
        }   // end else: value not null
    }   // end constructor



    /**
     * Create a KLV set with the given key, key length based on the length of the array,
     * length field encoding,
     * and provided value in a byte array. If <tt>value</tt> is <tt>null</tt>,
     * then a zero-length value is assumed.
     */
    public KLV( byte[] key, LengthEncoding lengthEncoding, byte[] value, int offset, int length ){

        // Check for bad parameters
        if( key == null )
            throw new NullPointerException( "Key must not be null." );
        if( lengthEncoding == null )
            throw new NullPointerException( "Length encoding must not be null." );
        if( !(key.length==1 || key.length==2 || key.length==4 || key.length==16) )
            throw new IllegalArgumentException( "Key length must be 1, 2, 4, or 16 bytes, not " + key.length );
        if( value != null ){
            if( offset < 0 || offset >= value.length )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Offset %d is out of range (byte array length: %d).",
                        offset, value.length ) );
            if( offset + length >= value.length )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Not enough bytes in array for declared length of %d.",
                        length ) );
        }   // end if: value not null


        // Key
        this.setKey( key, 0, KeyLength.valueOf(key.length) );

        // Length & value
        this.lengthEncoding = lengthEncoding;
        if( value == null ){
            this.value = new byte[0];
        } else {
            switch( lengthEncoding ){
            case OneByte:
                if( length > (1<<8)-1 )
                    throw new IllegalArgumentException(String.format(
                            "%s encoding cannot support a %d-byte value.",
                            lengthEncoding, length ) );
                this.value = new byte[length];
                System.arraycopy(value,offset, this.value,0,length);

            case TwoBytes:
                if( length > (1<<16)-1 )
                    throw new IllegalArgumentException(String.format(
                            "%s encoding cannot support a %d-byte value.",
                            lengthEncoding, length ) );
                this.value = new byte[length];
                System.arraycopy(value,offset, this.value,0,length);
                break;

            case FourBytes:
            case BER:
                // Any Java length is allowed.
                this.value = new byte[ length ];
                System.arraycopy(value,offset, this.value,0,length);
                break;

            default:
                assert false : lengthEncoding; // We've accounted for all types
            }
        }   // end else: value not null
    }   // end constructor






    /**
     * Return the KLV as a byte array.
     * The array is copied from the original underlying byte array.
     */
    public byte[] toBytes(){
        byte[] key = this.getFullKey();
        byte[] lengthField = KLV.makeLengthField(this.lengthEncoding, this.value.length);
        byte[] bytes = new byte[ key.length + lengthField.length + this.value.length ];

        System.arraycopy(key,0, bytes,0,key.length);
        System.arraycopy(lengthField,0, bytes,key.length,lengthField.length);
        System.arraycopy(this.value,0, bytes,key.length+lengthField.length,this.value.length);

        return bytes;
    }


    public static void main(String[] args){
            KLV klv;
        // Add one-byte subKLV
        for( int i = 0; i < 255; i++ ){
            klv = new KLV();
            klv.addSubKLV(42, (byte)i);
            klv.addSubKLV(23, (byte)((i+10)%255));
            KLV k42 = klv.getSubKLVMap().get(42);
            KLV k23 = klv.getSubKLVMap().get(23);
        }
    }



/* ********  P U B L I C   G E T   M E T H O D S  ******** */



    /**
     * Returns a list of all KLV sets in this payload (value field)
     * assuming the existing key length and length field encoding.
     */
    public List<KLV> getSubKLVList(){
        return this.getSubKLVList(this.keyLength, this.lengthEncoding);
    }



    /**
     * Returns a list of all KLV sets in this payload (value field)
     * assuming the given key length and length field encoding.
     */
    public List<KLV> getSubKLVList( KeyLength keyLength, LengthEncoding lengthEncoding ){
        return KLV.bytesToList(
                this.value,0,this.value.length, keyLength, lengthEncoding );
    }




    /**
     * Return a mapping of keys (up to four bytes long) to KLV sets
     * from this KLV's payload (value field) based on the existing
     * key length and length field encoding.
     * If two KLV subsets are in the payload, and they each have the
     * same key value, then the latter one will overwrite the earlier one.
     *
     */
    public Map<Integer,KLV> getSubKLVMap(){
        return this.getSubKLVMap(this.keyLength, this.lengthEncoding);
    }


    /**
     * Return a mapping of keys (up to four bytes long) to KLV sets
     * from this KLV's payload (value field) based on an assumed key
     * length and length field encoding scheme.
     * If two KLV subsets are in the payload, and they each have the
     * same key value, then the latter one will overwrite the earlier one.
     *
     */
    public Map<Integer,KLV> getSubKLVMap( KeyLength keyLength, LengthEncoding lengthEncoding ){
        return KLV.bytesToMap(
                this.value,0,this.value.length, keyLength, lengthEncoding );
    }




    /**
     * Returns the length of the key
     * (not necessarily of the payload within,
     * if the payload is more KLV data).
     *
     * @return length of key.
     */
    public KeyLength getKeyLength(){
        return this.keyLength;
    }   // end getKeyLength





    /**
     * Returns up to four bytes of the key as an int.
     * If the key is sixteen bytes long, then the lowest
     * four bytes are used to determine this short key
     * (which is officially meaningless).
     *
     * @return the key
     */
    public int getShortKey(){
        switch( this.keyLength ){
        case OneByte:   return this.keyIfShort & 0xFF;
        case TwoBytes:  return this.keyIfShort & 0xFFFF;
        case FourBytes: return this.keyIfShort;

        case SixteenBytes:
            assert this.keyIfLong != null;
            assert 16 == this.keyIfLong.length : this.keyIfLong.length;
            int key = 0;
            for( int i = 0; i < 4; i++ ){
                key |= (this.keyIfLong[13+i] & 0xFF) << (4-i)*8;
            }   // end for: four bytes
            return key;

        default:
            assert false : this.keyLength;
            return 0;
        }   // end switch

    }   // end getShortKey


    /**
     * Returns a byte array representing the key. This is a copy of the bytes
     * from the original byte set.
     *
     * @return the key
     */
    public byte[] getFullKey(){
        int length = this.keyLength.value;
        byte[] key = new byte[length];

        switch( this.keyLength ){
        case OneByte:
            key[0] = (byte)this.keyIfShort;
            break;

        case TwoBytes:
            key[0] = (byte)(this.keyIfShort >> 8);
            key[1] = (byte)this.keyIfShort;
            break;

        case FourBytes:
            key[0] = (byte)(this.keyIfShort >> 24);
            key[1] = (byte)(this.keyIfShort >> 16);
            key[2] = (byte)(this.keyIfShort >> 8);
            key[3] = (byte)this.keyIfShort;
            break;

        case SixteenBytes:
            assert this.keyIfLong != null;
            assert 16 == this.keyIfLong.length : this.keyIfLong.length;
            System.arraycopy(this.keyIfLong,0, key,0,16);
            break;

        default:
            assert false : this.keyLength;
        }   // end switch

        return key;
    }   // end getFullKey




    /**
     * Returns the length encoding flag
     * (not necessarily of the payload within,
     * if the payload is more KLV data).
     *
     * @return length field encoding flag
     */
    public LengthEncoding getLengthEncoding(){
        return this.lengthEncoding;
    }



    /**
     * Returns the length of the value in this KLV set.
     *
     * @return length of value
     */
    public int getLength(){
        return this.value.length;
    }



    /**
     * Returns the value of this KLV set.
     * This is the actual byte array, so changes to the byte array
     * change the KLV's actual value.
     *
     * @return the value
     */
    public byte[] getValue(){
        return this.value;
    }




    /**
     * Returns up to the first byte of the value as an 8-bit signed integer.
     *
     * @return the value as an 8-bit signed integer
     */
    public int getValueAs8bitSignedInt(){
        byte[] bytes = getValue();
        byte value = 0;
        if( bytes.length > 0 )
            value = bytes[0];
        return value;
    }   // end getValueAs8bitSignedInt


    /**
     * Returns up to the first byte of the value as an 8-bit unsigned integer.
     *
     * @return the value as an 8-bit unsigned integer
     */
    public int getValueAs8bitUnsignedInt(){
        byte[] bytes = getValue();
        int value = 0;
        if( bytes.length > 0 )
            value = bytes[0] & 0xFF;
        return value;
    }   // end getValueAs8bitSignedInt



    /**
     * Returns up to the first two bytes of the value as a 16-bit signed integer.
     *
     * @return the value as a 16-bit signed integer
     */
    public int getValueAs16bitSignedInt(){
        byte[] bytes = getValue();
        short value = 0;
        int length = bytes.length;
        int shortLen = length < 2 ? length : 2;
        for( int i = 0; i < shortLen; i++ )
            value |= (bytes[i] & 0xFF) << (shortLen*8 - i*8 - 8);
        return value;
    }   // end getValueAs16bitSignedInt


    /**
     * Returns up to the first two bytes of the value as a 16-bit unsigned integer.
     *
     * @return the value as a 16-bit unsigned integer
     */
    public int getValueAs16bitUnsignedInt(){
        byte[] bytes = getValue();
        int value = 0;
        int length = bytes.length;
        int shortLen = length < 2 ? length : 2;
        for( int i = 0; i < shortLen; i++ )
            value |= (bytes[i] & 0xFF) << (shortLen*8 - i*8 - 8);
        return value;
    }   // end getValueAs16bitUnsignedInt



    /**
     * Returns up to the first four bytes of the value as a 32-bit int.
     * Since all Java ints are signed, there is no signed/unsigned option.
     * If you need a 32-bit unsigned int, try {@link #getValueAs64bitLong}.
     *
     * @return the value as an int
     */
    public int getValueAs32bitInt(){
        byte[] bytes = getValue();
        int value = 0;
        int length = bytes.length;
        int shortLen = length < 4 ? length : 4;
        for( int i = 0; i < shortLen; i++ )
            value |= (bytes[i] & 0xFF) << (shortLen*8 - i*8 - 8);
        return value;
    }   // end getValueAs32bitSignedInt



    /**
     * Returns up to the first eight bytes of the value as a 64-bit signed long.
     * Note if you expect a 32-bit <b>unsigned</b> int, and since Java doesn't
     * have such a thing, you could return a long instead and get the proper effect.
     *
     * @return the value as a long
     */
    public long getValueAs64bitLong(){
        byte[] bytes = getValue();
        long value = 0;
        int length = bytes.length;
        int shortLen = length < 8 ? length : 8;
        for( int i = 0; i < shortLen; i++ )
            value |= (long)(bytes[i] & 0xFF) << (shortLen*8 - i*8 - 8);
        return value;
    }   // end getValueAs64bitLong



    /**
     * Returns the first four bytes of the value as a float according
     * to IEEE 754 byte packing. See Java's Float class for details.
     * This method calls <code>Float.intBitsToFloat</code> with
     * {@link #getValueAs32bitInt} as the argument. However it does check
     * to see that the value has at least four bytes. If it does not,
     * then <tt>Float.NaN</tt> is returned.
     *
     * @return the value as a float
     */
    public float getValueAsFloat(){
        return this.getValue().length < 4
                ? Float.NaN
                : Float.intBitsToFloat(getValueAs32bitInt());
    }   // end getValueAsFloat



    /**
     * Returns the first eight bytes of the value as a double according
     * to IEEE 754 byte packing. See Java's Double class for details.
     * This method calls <code>Double.longBitsToDouble</code> with
     * {@link #getValueAs64bitLong} as the argument. However it does check
     * to see that the value has at least eight bytes. If it does not,
     * then <tt>Double.NaN</tt> is returned.
     *
     * @return the value as a float
     */
    public double getValueAsDouble(){
        return this.getValue().length < 8
                ? Double.NaN
                : Double.longBitsToDouble(getValueAs64bitLong());
    }   // end getValueAsDouble





    /**
     * Returns the value as a String using KLV's default character set
     * as defined by {@link #DEFAULT_CHARSET_NAME} or the computer's default
     * charset if that is not available.
     *
     * @return value as a string
     */
    public String getValueAsString(){
        try{
            return getValueAsString( DEFAULT_CHARSET_NAME );
        } catch( java.io.UnsupportedEncodingException exc ){
            return new String( getValue() );
        }   // end catch
    }   // end getValueAsString



    /**
     * Return the value as a String, interpreted with given encoding.
     *
     * @return value as String.
     */
    public String getValueAsString( String charsetName ) throws java.io.UnsupportedEncodingException{
        return new String( getValue(), charsetName );
    }





/* ********  S E T   K E Y   M E T H O D S  ******** */





    /**
     * Sets the key length and discards any leftover bytes.
     * If sizing up, key is preserved. For instance, a one-byte
     * key of 42, when changed to a four-byte key, will still
     * be 42. When jumping to or from a sixteen byte key however,
     * the previous value of the key is discarded.
     *
     * @param keyLength     The new key length
     * @return              <tt>this</tt> to aid in stringing together commands
     */
    public KLV setKeyLength( KeyLength keyLength ){

        // No change? Bail out.
        if( this.keyLength == keyLength )
            return this;

        // Expanding to sixteen?
        if( keyLength == KeyLength.SixteenBytes ){
            this.keyIfShort = 0;
            this.keyIfLong = new byte[16];
        }   // end if: expanding to sixteen

        // Shrinking from sixteen?
        else if( this.keyLength == KeyLength.SixteenBytes ){
            this.keyIfShort = 0;
            this.keyIfLong = null;
        }   // end else if: shrinking from sixteen

        // Else, 1, 2, 4 switch-a-roos are no matter
        // Whoopie.

        this.keyLength = keyLength;
        return this;
    }



    public KLV setKey( byte[] key ){
        if( key == null )
            throw new NullPointerException( "Key must not be null." );

        switch( key.length ){
        case 1:
        case 2:
        case 4:
        case 16:
            return this.setKey( key, 0, KeyLength.valueOf(key.length));
        default:
            throw new IllegalArgumentException("Invalid key size: " + key.length );
        }
    }


    /**
     * Sets the key according to the key found in the byte array
     * and of the given length. If <tt>keyLength</tt> is different
     * than what was previously set for this KLV, then this KLV's
     * key length parameter will be updated.
     *
     * @param inTheseBytes  The byte array containing the key (and other stuff)
     * @param offset        The offset where to look for the key
     * @param keyLength     The length of the key
     * @return              <tt>this</tt> to aid in stringing together commands
     * @throws NullPointException               If any parameter is null
     * @throws ArrayIndexOutOfBoundsException   If offset is invalid
     */
    public KLV setKey( byte[] inTheseBytes, int offset, KeyLength keyLength ){

        // Check for null and bad offset
        if( inTheseBytes == null )
            throw new NullPointerException( "Byte array must not be null." );
        if( keyLength == null )
            throw new NullPointerException( "Key length must not be null." );
        if( offset < 0 || offset >= inTheseBytes.length )
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Offset %d is out of range (byte array length: %d).",
                    offset, inTheseBytes.length ) );
        if( inTheseBytes.length - offset < keyLength.value() )
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Not enough bytes for %d-byte key.", keyLength.value() ) );

        // Set key according to length of key
        this.keyLength = keyLength;
        switch( keyLength ){
        case OneByte:
            this.keyIfShort = inTheseBytes[offset] & 0xFF;
            this.keyIfLong  = null;
            break;

        case TwoBytes:
            this.keyIfShort  = (inTheseBytes[offset]   & 0xFF) << 8;
            this.keyIfShort |=  inTheseBytes[offset+1] & 0xFF;
            this.keyIfLong  = null;
            break;

        case FourBytes:
            this.keyIfShort  = (inTheseBytes[offset]   & 0xFF) << 24;
            this.keyIfShort |= (inTheseBytes[offset+1] & 0xFF) << 16;
            this.keyIfShort |= (inTheseBytes[offset+2] & 0xFF) << 8;
            this.keyIfShort |=  inTheseBytes[offset+3] & 0xFF;
            this.keyIfLong  = null;
            break;

        case SixteenBytes:
            this.keyIfLong = new byte[16];
            System.arraycopy(inTheseBytes,offset, this.keyIfLong,0,16);
            this.keyIfShort = 0;
            break;

        default:
            throw new IllegalArgumentException("Unknown key length: " + keyLength );
        }
        return this;
    }   // end setKey




    /**
     * Sets the key according to the existing key length.
     *
     * @param shortKey      the key of one, two, or four bytes
     * @return              <tt>this</tt> to aid in stringing commands together
     */
    public KLV setKey( int shortKey ){
        return setKey( shortKey, this.keyLength );
    }



    /**
     * Sets the key according to the given key length.
     * If you specify a sixteen-byte key, then the lowest four
     * bytes will be set to the four bytes in the int <tt>shortKey</tt>.
     *
     * @param shortKey      the key of one, two, or four bytes
     * @param keyLength     the length of the key
     * @return              <tt>this</tt> to aid in stringing commands together
     */
    public KLV setKey( int shortKey, KeyLength keyLength ){

        switch( keyLength ){
        case OneByte:
        case TwoBytes:
        case FourBytes:
            this.keyIfShort = shortKey;
            this.keyIfLong  = null;
            this.keyLength  = keyLength;
            break;

        case SixteenBytes:
            byte[] key = new byte[16];
            for( int i = 0; i < 4; i++ ){
                key[13+i] = (byte)(shortKey >> (3-i)*8);
            }   // end for: four bytes
            this.keyLength = keyLength;
            break;

        default:
            assert false : keyLength;
        }   // end switch

        return this;
    }   // end setKey



/* ********  S E T   L E N G T H   M E T H O D S  ******** */




    /**
     * Sets the length encoding used in this KLV set.
     * If necessary, the current value will be truncated if
     * the new length encoding cannot support the size of the value.
     * This would only be the case for one- and two-byte length encodings.
     *
     * @param lengthEncoding    The new length encoding
     * @return                  <tt>this</tt> to aid in stringing together commands
     */
    public KLV setLengthEncoding( LengthEncoding lengthEncoding ){

        switch( lengthEncoding ){
        case OneByte:
            if( this.value.length > (2<<8)-1 ){
                byte[] bytes = new byte[(2<<8)-1];
                System.arraycopy(this.value,0, bytes,0,bytes.length);
                this.value = bytes;
            }   // end if: need to truncate
            this.lengthEncoding = lengthEncoding;
            break;

        case TwoBytes:
            if( this.value.length > (2<<16)-1 ){
                byte[] bytes = new byte[(2<<16)-1];
                System.arraycopy(this.value,0, bytes,0,bytes.length);
                this.value = bytes;
            }   // end if: need to truncate
            this.lengthEncoding = lengthEncoding;
            break;

        case FourBytes:
        case BER:
            this.lengthEncoding = lengthEncoding;
            break;

        default:
            assert false : lengthEncoding;
        }   // end switch

        return this;
    }





    /**
     * Sets the length according to the length found in the byte array
     * and of the given length encoding.
     * If <tt>lengthEncoding</tt> is different
     * than what was previously set for this KLV, then this KLV's
     * length encoding parameter will be updated.
     * An array of the appropriate length will be initialized.
     *
     * @param inTheseBytes      The byte array containing the key (and other stuff)
     * @param offset            The offset where to look for the key
     * @param lengthEncoding    The length of the key
     * @return                  Offset where value field would begin after length
     * @throws NullPointException               If any parameter is null
     * @throws ArrayIndexOutOfBoundsException   If offset is invalid
     */
    public int setLength( byte[] inTheseBytes, int offset, LengthEncoding lengthEncoding ){

        // Check for null and bad offset
        if( inTheseBytes == null )
            throw new NullPointerException( "Byte array must not be null." );
        if( lengthEncoding == null )
            throw new NullPointerException( "Length encoding must not be null." );
        if( offset < 0 || offset >= inTheseBytes.length )
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Offset %d is out of range (byte array length: %d).",
                    offset, inTheseBytes.length ) );

        int length = 0;
        int valueOffset = 0;
        switch( lengthEncoding ){
        case OneByte:
            if( inTheseBytes.length - offset < 1 )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Not enough bytes for %s length encoding.", lengthEncoding ) );
            length = inTheseBytes[offset] & 0xFF;
            setLength( length, lengthEncoding );
            valueOffset = offset + 1;
            break;

        case TwoBytes:
            if( inTheseBytes.length - offset < 2 )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Not enough bytes for %s length encoding.", lengthEncoding ) );
            length  = (inTheseBytes[offset]   & 0xFF) << 8;
            length |=  inTheseBytes[offset+1] & 0xFF;
            setLength( length, lengthEncoding );
            valueOffset = offset + 2;
            break;

        case FourBytes:
            if( inTheseBytes.length - offset < 4 )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Not enough bytes for %s length encoding.", lengthEncoding ) );
            length  = (inTheseBytes[offset]   & 0xFF) << 24;
            length |= (inTheseBytes[offset+1] & 0xFF) << 16;
            length |= (inTheseBytes[offset+2] & 0xFF) << 8;
            length |=  inTheseBytes[offset+3] & 0xFF;
            setLength( length, lengthEncoding );
            valueOffset = offset + 4;
            break;

        case BER:
        // Short BER form: If high bit is not set, then
        // use the byte to determine length of payload.
        // Long BER form: If high bit is set (0x80),
        // then use low seven bits to determine how many
        // bytes that follow are themselves an unsigned
        // integer specifying the length of the payload.
        // Using more than four bytes to specify the length
        // is not supported in this code, though it's not
        // exactly illegal KLV notation either.
            if( inTheseBytes.length - offset < 1 )
                throw new ArrayIndexOutOfBoundsException( String.format(
                        "Not enough bytes for %s length encoding.", lengthEncoding ) );
            int ber = inTheseBytes[offset] & 0xFF;

            // Easy case: low seven bits is length
            if( (ber & 0x80) == 0 ){
            setLength( ber, lengthEncoding );
                valueOffset = offset + 1;
            }

            // Else, use following bytes to determine length
            else{
                int following = ber & 0x7F; // Low seven bits
                if( inTheseBytes.length - offset < following+1 )
                    throw new ArrayIndexOutOfBoundsException( String.format(
                            "Not enough bytes for %s length encoding.", lengthEncoding ) );
                for( int i = 0; i < following; i++ ){
                    length |= (inTheseBytes[offset+1+i] & 0xFF) << (following-1-i)*8;
                }
                setLength( length, lengthEncoding );
                valueOffset = offset + 1 + following;
            }
            break;

        default:
            assert false : lengthEncoding;
        }   // end switch

        return valueOffset;
    }




    /**
     * Sets the length of the Value, copying or truncating the old value
     * as appropriate for the new length and using the existing
     * length encoding.
     *
     * @param length            The new number of bytes in the Value
     * @return                  <tt>this</tt> to aid in stringing commands together
     */
    public KLV setLength( int length ){
        return this.setLength( length, this.lengthEncoding );
    }




    /**
     * Sets the length of the Value, copying or truncating the old value
     * as appropriate for the new length;
     *
     * @param length            The new number of bytes in the Value
     * @param lengthEncoding    The length encoding to use
     * @return                  <tt>this</tt> to aid in stringing commands together
     */
    public KLV setLength( int length, LengthEncoding lengthEncoding ){
        if( length < 0 )
            throw new IllegalArgumentException( "Length must not be negative: " + length );

        // Check errors based on length encoding
        switch( lengthEncoding ){
        case OneByte:
            if( length > (1<<8)-1 )
                throw new IllegalArgumentException(String.format(
                        "%s encoding cannot support a %d-byte value.",
                        lengthEncoding, length ) );
            break;

        case TwoBytes:
            if( length > (1<<16)-1 )
                throw new IllegalArgumentException(String.format(
                        "%s encoding cannot support a %d-byte value.",
                        lengthEncoding, length ) );
            break;

        case FourBytes: // Any Java length is allowed.
        case BER:       // Any Java length is allowed.
            break;

        default:
            assert false : lengthEncoding; // We've accounted for all types
        }   // end switch

        // Copy old value
        byte[] bytes = new byte[length];
        if( this.value != null ){
            System.arraycopy(value,0, bytes,0,(int)Math.min(length,this.value.length));
        }   // end if: value exists
        this.value = bytes;

        return this;
    }



/* ********  S E T   V A L U E   M E T H O D S  ******** */



    /**
     * Sets the value of the KLV set, throwing an
     * IllegalArgumentException if <tt>newValue</tt>
     * is too long for the existing length encoding.
     *
     * @param newValue      New value for the KLV set
     * @return              <tt>this</tt> to aid in stringing commands together.
     */
    public KLV setValue( byte[] newValue ){
        return setValue( newValue, 0, newValue.length );
    }


    /**
     * Sets the value of the KLV set and adjusts the length encoding as specified.
     * IllegalArgumentException if <tt>newValue</tt>
     * is too long for the existing length encoding.
     */
    public KLV setValue( byte[] newValue, int offset, int length ){

        // Check for null and bad offset
        if( newValue == null )
            throw new NullPointerException( "Byte array must not be null." );
        if( offset < 0 )
            throw new ArrayIndexOutOfBoundsException( "Offset must not be negative: " + offset );
        if( value.length > 0 && offset >= value.length ) // Empty arrays are OK
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Offset %d is out of range (byte array length: %d).",
                    offset, value.length ) );

        if( newValue.length - offset < length ){
            throw new IllegalArgumentException(String.format(
                    "Number of bytes (%d) and offset (%d) not sufficient for declared length (%d).",
                    newValue.length, offset, length ));
        }


        // Check errors based on length encoding
        switch( this.lengthEncoding ){
        case OneByte:
            if( length > (1<<8)-1 )
                throw new IllegalArgumentException(String.format(
                        "%s encoding cannot support a %d-byte value.",
                        this.lengthEncoding, length ) );
            break;

        case TwoBytes:
            if( length > (1<<16)-1 )
                throw new IllegalArgumentException(String.format(
                        "%s encoding cannot support a %d-byte value.",
                        this.lengthEncoding, length ) );
            break;

        case FourBytes: // Any Java length is allowed.
        case BER:       // Any Java length is allowed.
            break;

        default:
            assert false : this.lengthEncoding; // We've accounted for all types
        }   // end switch

        // Copy old value
        byte[] bytes = new byte[length];
        System.arraycopy(newValue,offset, bytes,0,length);
        this.value = bytes;

        return this;
    }






/* ********  A D D   M E T H O D S  ******** */





    /**
     * Adds a sub KLV set with the given key and the
     * single byte of data as the payload
     * using the parent's key length
     * and parent's length field encoding.
     *
     * @param key   The key for the data
     * @param subValue  The data in the payload
     * @return      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int key, byte subValue ){
        return addSubKLV( key, new byte[]{ subValue } );
    }   // end addSubKLV



    /**
     * Adds a sub KLV set with the given key and the
     * single short (two bytes) of data as the payload
     * using the parent's key length
     * and parent's length field encoding.
     *
     * @param key   The key for the data
     * @param subValue  The data in the payload
     * @return      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int key, short subValue ){
        return addSubKLV( key, new byte[]{ (byte)(subValue >> 8), (byte)subValue } );
    }   // end addSubKLV



    /**
     * Adds a sub KLV set with the given key and the
     * single int (four bytes) of data as the payload
     * using the parent's key length
     * and parent's length field encoding.
     *
     * @param key   The key for the data
     * @param subValue  The data in the payload
     * @return      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int key, int subValue ){
        return addSubKLV( key, new byte[]{
            (byte)(subValue >> 24),
            (byte)(subValue >> 16),
            (byte)(subValue >>  8),
            (byte)subValue } );
    }   // end addSubKLV



    /**
     * Adds a sub KLV set with the given key and the
     * single long (eight bytes) of data as the payload
     * using the parent's key length
     * and parent's length field encoding.
     *
     * @param key   The key for the data
     * @param subValue  The data in the payload
     * @return      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int key, long subValue ){
        return addSubKLV( key, new byte[]{
            (byte)(subValue >> 56),
            (byte)(subValue >> 48),
            (byte)(subValue >> 40),
            (byte)(subValue >> 32),
            (byte)(subValue >> 24),
            (byte)(subValue >> 16),
            (byte)(subValue >>  8),
            (byte)subValue } );
    }   // end addSubKLV


    /**
     * Adds a sub KLV set with the given key and the
     * string of data as the payload
     * using the parent's key length
     * and parent's length field encoding.
     * If data is <tt>null</tt>, then the corresponding
     * payload length will be zero.
     * The default charset (UTF-8) will be used unless
     * that is not supported in which case the current
     * computer's default charset will be used.
     *
     * @param key   The key for the data
     * @param subValue  The data in the payload
     * @return      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int key, String subValue ){
        if( subValue == null ){
            return addSubKLV( key, new byte[0] );
        }   // end if: null
        else {
            try{
                return addSubKLV( key, subValue.getBytes(KLV.DEFAULT_CHARSET_NAME) );
            } catch( java.io.UnsupportedEncodingException exc ){
                return addSubKLV( key, subValue.getBytes() );
            }   // end catch
        }   // end else: not null
    }   // end addSubKLV


    /**
     * Adds a KLV set to the overall payload using the given
     * key, parent's key length, parent's length encoding, and the provided data.
     * Underlying byte array is copied and replaced.
     *
     * @param key   The key for the data
     * @param subValue  The data in the payload
     * @return      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int key, byte[] subValue ){
        return addSubKLV( key, this.keyLength, this.lengthEncoding, subValue );
    }   // end addSubKLV





    /**
     * Adds a KLV set to the overall payload using the given
     * key, given sub key length, given length encoding, and the provided data.
     * Underlying byte array is copied and replaced.
     *
     * @param subKey                The key for the data
     * @param subKeyLength          Length of key in sub KLV
     * @param subLengthEncoding     Length field encoding in sub KLV
     * @param subValue              The data in the payload
     * @return                      <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( int subKey, KeyLength subKeyLength,
            LengthEncoding subLengthEncoding, byte[] subValue ){

        return addSubKLV( new KLV( subKey, subKeyLength, subLengthEncoding,
                subValue, 0, subValue.length ) );

    }   // end addSubKLV


    /**
     * Adds the given KLV set to the payload by calling
     * <code>addPaylaod( sub.toBytes() )</code>.
     *
     * @param sub the KLV set to add.
     * @return <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addSubKLV( KLV sub ){
        return addPayload( sub.toBytes() );
    }




    /**
     * Adds the provided bytes to the payload and adjusts the length field.
     *
     * @param extraBytes    New bytes to add
     * @return              <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addPayload( byte[] extraBytes ){
        addPayload( extraBytes, 0, extraBytes.length );
        return this;
    }

    /**
     * Adds the provided bytes to the payload and adjusts the length field.
     * If the length field encoding does not support payloads as large
     * as would result from adding <tt>extraBytes</tt>,
     * then an IllegalArgumentException is thrown.
     *
     * @param extraBytes    new bytes to add
     * @param extraOffset   offset within <code>extraBytes</code>
     * @param extraLength   length of <code>extraBytes</code> to use
     * @return              <tt>this</tt>, to aid in stringing commands together.
     */
    public KLV addPayload( byte[] bytes, int offset, int length ){

        if( bytes == null )
            throw new NullPointerException( "Byte array must not be null." );
        if( offset < 0 || offset >= bytes.length )
            throw new ArrayIndexOutOfBoundsException( String.format(
                    "Offset %d is out of range (byte array length: %d).",
                    offset, bytes.length ) );
        if( bytes.length - offset < length ){
            throw new IllegalArgumentException(String.format(
                    "Number of bytes (%d) and offset (%d) not sufficient for declared length (%d).",
                    bytes.length, offset, length ));
        }

        int newLength = this.value.length + length;

        // Check errors based on length encoding
        switch( this.lengthEncoding ){
        case OneByte:
            if( newLength > (1<<8)-1 )
                throw new IllegalArgumentException(String.format(
                        "%s encoding cannot support a %d-byte value.",
                        this.lengthEncoding, newLength ) );
            break;

        case TwoBytes:
            if( newLength > (1<<16)-1 )
                throw new IllegalArgumentException(String.format(
                        "%s encoding cannot support a %d-byte value.",
                        this.lengthEncoding, newLength ) );
            break;

        case FourBytes: // Any Java length is allowed.
        case BER:       // Any Java length is allowed.
            break;

        default:
            assert false : this.lengthEncoding; // We've accounted for all types
        }   // end switch

        byte[] newValue = new byte[ newLength ];
        System.arraycopy(this.value,0, newValue,0,this.value.length);
        System.arraycopy(bytes,offset, newValue,this.value.length,length);
        this.value = newValue;

        return this;
    }







/* ********  O B J E C T   O V E R R I D E  ******** */



    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append('[');

        // Key
        sb.append("Key=");
        if( this.keyLength.value() <= 4 ) sb.append( getShortKey() );
        else{
            sb.append('[');
            byte[] longKey = getFullKey();
            for( byte b : longKey )
                sb.append(Long.toHexString(b & 0xFF)).append(' ');
            sb.append(']');
        }

        // Length
        sb.append(", Length=");
        sb.append( getLength() );

        // Value
        sb.append(", Value=[");
        byte[] value = getValue();
        for( byte b : value )
            sb.append(Long.toHexString(b & 0xFF)).append(' ');
        sb.append(']');

        sb.append(']');
        return sb.toString();
    }





/* ********  S T A T I C   M E T H O D S  ********* */



    /**
     * Returns a list of KLV sets in the supplied byte array
     * assuming the provided key length and length field encoding.
     *
     * @param bytes             The byte array to parse
     * @param offset            Where to start parsing
     * @param length            How many bytes to parse
     * @param keyLength         Length of keys assumed in the KLV sets
     * @param lengthEncoding    Flag indicating encoding type
     * @return                  List of KLVs
     */
    public static java.util.List<KLV> bytesToList(
            byte[] bytes, int offset, int length,
            KeyLength keyLength, LengthEncoding lengthEncoding ){
        LinkedList<KLV> list = new LinkedList<KLV>();

        int currentPos = offset;    // Keep track of where we are
        while( currentPos < offset + length ){
            try{
                KLV klv = new KLV( bytes, currentPos, keyLength, lengthEncoding );
                currentPos = klv.offsetAfterInstantiation; // private access

                list.add( klv );
            } catch( Exception exc ){
                // Stop trying for more?
                System.err.println("Stopped parsing with exception: " + exc.getMessage() );
                break;
            }   // end catch

        }   // end while

        return list;
    }   // end parseBytes



    /**
     * Return a mapping of keys (up to four bytes) to KLV
     * sets based on an assumed key length and length field
     * encoding scheme. If two KLV subsets are in the payload,
     * and they each have the same key value, then the latter
     * one will overwrite the earlier one.
     *
     * @param bytes             The byte array to parse
     * @param offset            Where to start parsing
     * @param length            How many bytes to parse
     * @param keyLength         Length of keys assumed in the KLV sets
     * @param lengthEncoding    Flag indicating encoding type
     * @return                  Map of keys to KLVs
     */
    public static Map<Integer,KLV> bytesToMap(
            byte[] bytes, int offset, int length,
            KeyLength keyLength, LengthEncoding lengthEncoding ){

        Map<Integer,KLV> map = new HashMap<Integer,KLV>();

        for( KLV klv : KLV.bytesToList(bytes, offset, length, keyLength, lengthEncoding) ){
            map.put( klv.getShortKey(),klv );
        }

        return map;
    }   // end parseBytes





    /**
     * Make a byte array that represents the length field necessary to
     * indicate the given payload length. Most useful when using BER encoding.
     *
     * @param lengthEncoding    field encoding flag
     * @param payloadLength     number of bytes in value
     * @return                  byte array with appropriate length field bytes
     */
    protected static byte[] makeLengthField( LengthEncoding lengthEncoding, int payloadLength ){

        // Bytes for length encoding
        byte[] bytes = null;
        switch( lengthEncoding ){

            // Unsigned integer, one byte long.
            case OneByte:
                if( payloadLength > 255 )
                    throw new IllegalArgumentException(
                        String.format("Too much data (%d bytes) for one-byte length field encoding.", payloadLength) );
                bytes = new byte[]{ (byte)payloadLength };
                break;

            // Unsigned integer, two bytes long, big endian.
            case TwoBytes:
                if( payloadLength > 65535 )
                    throw new IllegalArgumentException(
                        String.format("Too much data (%d bytes) for two-byte length field encoding.", payloadLength) );
                bytes = new byte[]{ (byte)(payloadLength >> 8), (byte)payloadLength };
                break;

            // (Un?)signed integer, four bytes long, big endian.
            case FourBytes:
                bytes = new byte[]{
                    (byte)(payloadLength >> 24),
                    (byte)(payloadLength >> 16),
                    (byte)(payloadLength >>  8),
                    (byte)payloadLength };
                break;

            // Short BER form: If high bit is not set, then
            // use the byte to determine length of payload.
            // Long BER form: If high bit is set (0x80),
            // then use low seven bits to determine how many
            // bytes that follow are themselves an unsigned
            // integer specifying the length of the payload.
            // Using more than four bytes to specify the length
            // is not supported in this code, though it's not
            // exactly illegal KLV notation either.
            case BER:
                if( payloadLength <= 127 ){
                    bytes = new byte[]{ (byte)payloadLength };
                }   // end if: short form
                else {
                    if( payloadLength <= 255 ){ // One byte
                        bytes = new byte[]{ (byte)0x81, (byte)payloadLength };
                    } else if( payloadLength <= 65535 ){ // Two bytes
                        bytes = new byte[]{ (byte)0x82, (byte)(payloadLength >> 8), (byte)payloadLength };
                    } else { // Four bytes
                    bytes = new byte[]{
                        (byte)0x84,
                        (byte)(payloadLength >> 24),
                        (byte)(payloadLength >> 16),
                        (byte)(payloadLength >>  8),
                        (byte)payloadLength };
                    }
                }   // end else: long form
                    break;
            default:
                throw new IllegalStateException( "Unknown length field encoding flag: " + lengthEncoding );
        }   // end switch
        return bytes;
    }   // end makeLengthField





}   // end class KLV
