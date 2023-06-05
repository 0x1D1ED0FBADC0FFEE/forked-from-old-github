from frame import *
from switchAS import *
from switchCS import *
from node import *
import time
import threading
from ccsdefs import *


#Basically the main file, CCS and CAS and nodes are hard coded currently
#Everything is started as separate threads

def rawSetupTest():#initialize 10 nodes, 5 CAS, 1 CCS, send 20 frames
    ccs = switchCS()
    #ccs.start()
    ccst= threading.Thread(target=ccs.start, args=())
    ccst.start()
    time.sleep(0.5)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    print ("making test connection with CCS")

    port = CCS_PORT
        
    s.connect(("localhost", port))    

    time.sleep(0.25)
   
    cas1 = switchAS(1, 5000)
    cas2 = switchAS(2, 5010)
    cas3 = switchAS(3, 5020)
    cas4 = switchAS(4, 5030)
    cas5 = switchAS(5, 5040)

    node11 = node("1_1", 5000)
    node12 = node("1_2", 5000)

    node21 = node("2_1", 5010)
    node22 = node("2_2", 5010)

    node31 = node("3_1", 5020)
    node32 = node("3_2", 5020)

    node41 = node("4_1", 5030)
    node42 = node("4_2", 5030)

    node51 = node("5_1", 5040)
    node52 = node("5_2", 5040)

    cas1t= threading.Thread(target=cas1.start, args=())
    cas2t= threading.Thread(target=cas2.start, args=())
    cas3t= threading.Thread(target=cas3.start, args=())
    cas4t= threading.Thread(target=cas4.start, args=())
    cas5t= threading.Thread(target=cas5.start, args=())

    node11t= threading.Thread(target=node11.start, args=())
    node12t= threading.Thread(target=node12.start, args=())

    node21t= threading.Thread(target=node21.start, args=())
    node22t= threading.Thread(target=node22.start, args=())

    node31t= threading.Thread(target=node31.start, args=())
    node32t= threading.Thread(target=node32.start, args=())

    node41t= threading.Thread(target=node41.start, args=())
    node42t= threading.Thread(target=node42.start, args=())

    node51t= threading.Thread(target=node51.start, args=())
    node52t= threading.Thread(target=node52.start, args=())

    cas1t.start()
    cas2t.start()
    cas3t.start()
    cas4t.start()
    cas5t.start()

    node11t.start()
    node12t.start()

    node21t.start()
    node22t.start()

    node31t.start()
    node32t.start()

    node41t.start()
    node42t.start()

    node51t.start()
    node52t.start()


    node11t.join()
    node12t.join()

    node21t.join()
    node22t.join()

    node31t.join()
    node32t.join()

    node41t.join()
    node42t.join()

    node51t.join()
    node52t.join()

    cas1.shutdown()
    cas2.shutdown()
    cas3.shutdown()
    cas4.shutdown()
    cas5.shutdown()

    cas1t.join()
    cas2t.join()
    cas3t.join()
    cas4t.join()
    cas5t.join()

    ccs.shutdown()
    ccst.join()


if __name__ == "__main__":

    rawSetupTest()
