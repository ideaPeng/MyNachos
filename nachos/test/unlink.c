#include "syscall.h"



int  main(){

	int status = unlink("a.txt");
	if(status == 0){
		
		printf("Unlink the file successful\n");

	}
	halt();

	return 0;

}
