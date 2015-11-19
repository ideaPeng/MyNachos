#include "syscall.h"


int main(){
	
	char content[] ={"to be write\n"};
	int fid = creat("create.txt");//创建一个文件，返回文件描述符
	int wcount = write(fid,content,20);//写一个文件，返回写入的字符数
	
	int id = open("create.txt");//打开一个文件，返回文件描述符
	char buffer[20];
	read(id,buffer,20);//读一个文件
	printf("the content read from the file\n");
	printf(buffer);

	int status = close(id);//关闭一个文件
	if(status == 0){
		printf("Close file successful!\n");

	}



	char *argv[] ={"add.coff"};
	int eid = exec("add.coff",1,argv);
	join(eid,0);
	printf("the id returned by exec is %d\n",eid);
	
	halt();
	return 0;
}
