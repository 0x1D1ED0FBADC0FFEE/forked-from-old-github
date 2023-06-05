#Specification of offsets of fields in received frames

DST_FIELD = 0 #byte that has destination
SRC_FIELD = 1 #byte that contains the source
CRC_FIELD = 2 #byte that contains CRC
SIZE_FIELD = 3 #identifies the byte that contains the size of the data field
ACK_TYPE_FIELD = 4 #this byte has the ACK type
DATA_FIELD = 5 #offset of data field
BYTES_OF_FIELDS = 5 #how many bytes total before data