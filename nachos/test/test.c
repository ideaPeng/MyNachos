#include "syscall.h"


int main(){
	
	char content[] ={"to be write"};
	int fid = creat("create.txt");
	int wcount = write(fid,content,20);
	
	int id = open("create.txt");
	char buffer[20];
	read(id,buffer,20);
	printf(buffer);
	return 0;
}
