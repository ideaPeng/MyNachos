#include "syscall.h"

int main(){
	
	int a = 4;
	int b = 3;

	int c = a+b;

	printf("Now the child progress joined in\n");
	printf("In child progress,the result is %d\n",c);

	return 0;


}
