import re
def jhash(s):
    h=0
    b=s.encode('utf-16-le')
    for i in range(0,len(b),2):
        h=(31*h+int.from_bytes(b[i:i+2],'little'))&0xFFFFFFFF
    return h
def u16len(s): return len(s.encode('utf-16-le'))//2
def u16sub(s,n): return s.encode('utf-16-le')[:2*n].decode('utf-16-le',errors='ignore')
def app_file(book):
    name=re.sub(r'[\\/:*?"<>|\x00-\x1f\x7f]','_',book).strip() or 'book'
    if u16len(name)>120: name=u16sub(name,100)+'-'+format(jhash(book),'x')
    return name+'.json'
