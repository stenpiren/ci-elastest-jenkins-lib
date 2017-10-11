//With classes
class elastest_lib implements Serializable {
	
	//info of the ElasTest
	private String ip
	private String port
	
	//parameters for the ElasTest
	private boolean shared = false
	private String lite = ''
	def ctx
	
	/*
	*	Initialization of the context. It is mandatory for the correct usage of the library
	*/
	def setContext(value){ this.@ctx = value }
	
	/*
	*	Initialization of the parameter Lite just for personalization for normal execution leave empty
	*	for lite execution initialize with '--lite'
	*/
	def setLite(String value) { this.@lite = value }

	/*
	*	Initialization of the shared ElasTest for multiple jobs
	*/
	def setShared(String value) { this.@shared = value }
	
	/*
	*	@return elastest_ip
	*/
	def getIp() { return this.@ip }

	/*
	*	@return elastest_port
	*/
	def getPort() { return this.@port }
	
	/*
	*	When using methods of this library inside any provided step should be called through the 
	*	returned object by this method
	*	@return this object (executable library)
	*/
	def getElastestMethods(){ return this }
	
	/*
	*	Start elastest if it is not running
	*	@return boolean
	*/
	def startElastest(){
		echo '[INI] startElastest'
		
		def start_elastest_result = this.@ctx.sh script: 'docker run -d --name="elastest_platform" -v /var/run/docker.sock:/var/run/docker.sock --rm elastest/platform start --forcepull '+ lite, returnStatus:true
		echo 'startElastest-- start_elastest_result = '+start_elastest_result
		
		echo '[END] startElastest'
		return (start_elastest_result==0)
	}
	
	/*
	*	Check if ElasTest is running (platform and etm)
	*	@return boolean
	*/
	def elastestIsRunning(){
		echo '[INI] elastestIsRunning'
		def platform_state = this.@ctx.sh script: 'docker ps | grep elastest_platform | grep -c Up', returnStatus:true
		def etm_state = this.@ctx.sh script: 'docker ps | grep etm_1 | grep -c Up', returnStatus:true
		echo '[END] elastestIsRunning'
		return (platform_state==0 && etm_state==0)
	}

	/*
	*	Wait for Elastest to run (unconfigurable, just the provided wait in the toolbox)
	*	@return boolean
	*/
	def waitElastest(){
		echo '[INI] waitElastest'
		def elastest_is_running = this.@ctx.sh script: 'docker run --rm -v /var/run/docker.sock:/var/run/docker.sock elastest/platform wait', returnStatus:true
		echo '[END] waitElastest'
		return (elastest_is_running == 0)
	}

	/*
	*	StopElastest if it's running
	*/
	def stopElastest(){
		echo '[INI] stopElastest'
		def start_elastest_result = this.@ctx.sh script: 'docker run -d -v /var/run/docker.sock:/var/run/docker.sock --rm elastest/platform stop', returnStatus:true
		echo 'start_elastest_result = '+start_elastest_result	
		echo '[END] stopElastest'
	}
	
	/*
	*	Get the API
	*/
	def getApi(){
		echo '[INI] getAPI'
		def get_api = this.@ctx.sh script: 'echo $(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock elastest/platform inspect --api)', returnStdout:true
		//this is valid for output:
		/*
		Waiting for ETM...
		Platform inserted into network succesfully

		Container created with IP: 172.18.0.19
		ETM is ready in http://172.18.0.19:8091

		ElasTest API info:
		Url: http://172.18.0.19:8091
		*/
		
		def strings = get_api.tokenize( ':' )
		
		this.@ip = strings[strings.size()-2]
		this.@port = strings[strings.size()-1]
		echo '[END] getAPI  ip:port ' +this.@ip+':'+this.@port
	}
	
	/*
	*	predefined pipeline for executing e2e test over a managed ElasTest platform
	*/
	def pipeline(body) {
		
		def config = [:] //values for configure the job
						 //in a future version of the components? by default latest
						 // 
		body.resolveStrategy = Closure.DELEGATE_FIRST
		body.delegate = config
		println ("BODY: "+this.@shared)
		
		if (this.@shared == true ){
			this.@ctx.node ('sharedElastest'){
				this.@ctx.stage ('launch elastest' )			
					echo "sharedElastest ="+this.@shared
									
					def elastest_is_running = elastestIsRunning()
					echo "elastest_is_running? "+ elastest_is_running
					
					if (!elastest_is_running){
						echo 'ElasTest is not running...'
						echo 'START Shared ElasTest'
						startElastest()
						//lets set more time for waiting
						def counter = 3
						while (!elastest_is_running && counter > 0){
							elastest_is_running = waitElastest()
							counter = counter -1
							def return_status = this.@ctx.sh script: 'docker ps | grep elastest_', returnStatus:true
							echo 'elastest_is_running:'+elastest_is_running
						}
							
						def return_status = this.@ctx.sh script: 'docker ps | grep elastest_', returnStatus:true
						if (! elastest_is_running){
							this.@ctx.currentBuild.result = 'FAILURE'
							return
						}
						else {
							getApi()
						}
					}
					else {
						echo 'TODO: provide elastest feedback'
					}
				//body of the pipeline
				echo '[INI] User stages'
				body();	
				echo '[END] User stages'
				
				this.@ctx.stage ('release elastest')
					echo ('Shared elastest wont be ende because other jobs would be using it')
			}
		}
		else {
			this.@ctx.node('commonE2E'){
				this.@ctx.stage ('launch elastest')
					echo "sharedElastest ="+this.@shared
					def elastest_is_running = elastestIsRunning()
					if (elastest_is_running){ //stop and start again --> elastest is unique and frethis.@ctx.sh with each start
						stopElastest()
						sleep(10)//TODO: change for method like waitToStop or something like that
						elastest_is_running = elastestIsRunning()
						echo 'elastest_is_running:'+elastest_is_running
						if (elastest_is_running){
							this.@ctx.currentBuild.result = 'FAILURE'
							return
						}	
					}
					startElastest()
					//lets set more time for waiting
					def counter = 3
					while (!elastest_is_running && counter > 0){
						elastest_is_running = waitElastest()
						def return_status = this.@ctx.sh script: 'docker ps | grep elastest_', returnStatus:true
						echo 'elastest_is_running:'+elastest_is_running
						counter = counter -1
					}

					if (! elastest_is_running){
						this.@ctx.currentBuild.result = 'FAILURE'
						return
					}
					else {
							getApi()
					}
					
				//body of the pipeline	
				echo '[INI] User stages'
				body();	
				echo '[END] User stages'
				
				this.@ctx.stage ('release elastest')
					stopElastest()
			}
		}
	}
	
	/*
	*	Override for echo as if not accessed in the context doesn't work
	*/
	def echo (String str){
		this.@ctx.sh 'echo '+str
	}
	
}



