# NBT Format

The Named Binary Tag (NBT) is a tree data structure used by Minecraft in many save files to store arbitrary data. The
format comprises a handful of tags. Tags have a numeric type ID, a name, and a payload.

## File Structure

All files have a root TAG_Compound. They are stored in big-endian byte order.

The file can be compressed. If compressed, the first two bytes indicate the compression type:

- GZIP: 0xFE 0x8B
- ZLIB: 0x78 0x9C

## Tag Types

| Type ID | HEX  | Name           | Payload Description                                                                                                                                                                                                                               |
|---------|------|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0       | 0x00 | TAG_End        | No payload                                                                                                                                                                                                                                        |
| 1       | 0x01 | TAG_Byte       | 1 byte / 8 bits, signed                                                                                                                                                                                                                           |
| 2       | 0x02 | TAG_Short      | 2 bytes / 16 bits, signed, big endian                                                                                                                                                                                                             |
| 3       | 0x03 | TAG_Int        | 4 bytes / 32 bits, signed, big endian                                                                                                                                                                                                             |
| 4       | 0x04 | TAG_Long       | 8 bytes / 64 bits, signed, big endian                                                                                                                                                                                                             |
| 5       | 0x05 | TAG_Float      | 4 bytes / 32 bits, signed, big endian, IEEE 754-2008, binary32                                                                                                                                                                                    |
| 6       | 0x06 | TAG_Double     | 8 bytes / 64 bits, signed, big endian, IEEE 754-2008, binary64                                                                                                                                                                                    |
| 7       | 0x07 | TAG_Byte_Array | 4 bytes / 32 bits, signed, big endian for size, then the bytes of length size                                                                                                                                                                     |
| 8       | 0x08 | TAG_String     | 2 bytes / 16 bits, unsigned, big endian for size, then the bytes of length size as UTF-8 formatted character data. not null-terminated                                                                                                            |
| 9       | 0x09 | TAG_List       | 1 byte / 8 bits for the tag ID of the list's contents, then 4 bytes / 32 bits, big-endian for size. Followed by length size number of items of tag id                                                                                             |
| 10      | 0x0A | TAG_Compound   | Contains any number of tags, delimited by TAG_End. Each tag consisting of 1 byte / 8 bits tag ID, followed by 2 bytes / 16 bits, unsigned, big-endian for size, then an UTF-8 formatted string containing the tag name. Lastly, the payload data. |
| 11      | 0x0B | TAG_Int_Array  | 4 bytes / 32 bits, signed, big-endian for size, then size number of TAG_Int payloads.                                                                                                                                                             |
| 12      | 0x0C | TAG_Long_Array | 4 bytes / 32 bits, signed, big-endian for size, then size number of TAG_Long payloads.                                                                                                                                                            |

The List and Compound tags can contain other tags, allowing for nested structures. The maximum depth of nesting is 512.
