
#Frame specification class, kept simple to be self-documenting

class frame:
    def __init__(self, dest, src, crc, size, acktype, data):
        self.dest = dest.to_bytes(1,'big')
        self.src = src.to_bytes(1,'big')
        self.size = size.to_bytes(1,'big')
        self.data = data.encode()
        self.data = self.data
        #print("FRAME DATA", self.data)
    
        self.crc = b'\x00'
        self.acktype = acktype.to_bytes(1,'big')

    def getByteFrame(self):
        temp = b''
        temp = temp.join([  self.dest, 
                            self.src,
                            self.crc, 
                            self.size, 
                            self.acktype, 
                            self.data
                        ])
        return temp

    
